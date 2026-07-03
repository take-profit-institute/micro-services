package org.profit.candle.trading.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.event.ReservationExecutedPayload;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 종가 배치 체결의 건별 트랜잭션 실행 단위.
 *
 * <p>Spring AOP self-invocation 방지를 위해 별도 @Service로 분리했다.
 * 이 클래스의 모든 public 메서드는 외부(DefaultReservationBatchService)에서만 호출해야 한다 —
 * 내부에서 자기 자신의 메서드를 호출하면 프록시를 우회해 @Transactional이 무시된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationBatchExecutor {

    private final ReservationRepository reservationRepository;
    private final AccountService accountService;
    private final OutboxWriter outboxWriter;
    private final ReservationOutboxOperations outboxOperations;

    /** 락 획득 후 RESERVED 여부만 확인하고 즉시 커밋 — 락 보유시간 최소화. */
    @Transactional
    public boolean checkReservedUnderLock(UUID reservationId) {
        ReservationEntity reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElse(null);
        return reservation != null && reservation.reserved();
    }

    /** FAILED 전이 — 별도 트랜잭션으로 커밋 보장. */
    @Transactional
    public void markFailedUnderLock(UUID reservationId) {
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(reservation -> {
            if (reservation.reserved()) {
                reservation.markFailed();
                reservationRepository.save(reservation);
            }
        });
    }

    /**
     * lockBalance를 REQUIRES_NEW 트랜잭션으로 실행한다.
     * AccountException이 외부 트랜잭션을 rollback-only로 마킹하는 것을 방지한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void lockBalanceInNewTransaction(UUID userId, long amount) {
        accountService.lockBalance(userId, amount);
    }

    /**
     * releaseBalance를 REQUIRES_NEW 트랜잭션으로 실행한다.
     * lockBalance 보상용 — executeUnderLock 실패/no-op 시 DefaultReservationBatchService가
     * 직접 호출한다. self-invocation을 막기 위해 이 클래스 내부에서는 절대 호출하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseBalanceInNewTransaction(UUID userId, long amount) {
        if (userId == null || amount <= 0) return;
        accountService.releaseBalance(userId, amount);
    }

    /**
     * EXECUTED 전이 + Outbox 기록 — 트랜잭션 보장.
     *
     * <p>락 재획득 시점에 RESERVED가 아니면 false 반환. 보상(releaseBalance)은
     * 호출 측(DefaultReservationBatchService)이 try-finally로 처리한다.</p>
     *
     * @return EXECUTED 전이 성공 여부
     */
    @Transactional
    public boolean executeUnderLock(UUID reservationId, long executedPrice, long reservedAmount) {
        ReservationEntity reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElse(null);

        if (reservation == null || !reservation.reserved()) {
            return false;
        }

        reservation.markExecuted();
        reservationRepository.save(reservation);

        outboxWriter.record(outboxOperations, "ReservationExecuted",
                reservation.getId().toString(),
                new ReservationExecutedPayload(
                        reservation.getId().toString(),
                        reservation.getUserId().toString(),
                        reservation.getAccountId().toString(),
                        reservation.getSymbol(),
                        reservation.getSide().name(),
                        reservation.getQuantity(),
                        executedPrice,
                        reservedAmount));
        return true;
    }
}