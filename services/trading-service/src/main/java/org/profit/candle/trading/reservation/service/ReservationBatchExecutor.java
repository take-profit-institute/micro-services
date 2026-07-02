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
 * <p>{@link DefaultReservationBatchService}에서 내부 메서드를 @Transactional로 선언해도
 * Spring AOP self-invocation 때문에 프록시를 우회해 트랜잭션이 적용되지 않는다.
 * 이를 방지하기 위해 트랜잭션 경계가 필요한 메서드들을 별도 @Service로 분리했다.</p>
 *
 * <p>Qodo 지적사항 반영:</p>
 * <ul>
 *   <li>#1 (rollback-only): lockBalance는 REQUIRES_NEW로 분리 — AccountException이
 *       외부 트랜잭션을 rollback-only로 마킹하지 않도록 한다.</li>
 *   <li>#2 (락 보유시간): 락 획득 → 상태 검증만 하고 즉시 커밋 → 외부 호출(gRPC/잔고) →
 *       다시 락 획득 → 상태 전이 순서로 락 보유시간을 최소화한다.</li>
 * </ul>
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

    /** EXECUTED 전이 + Outbox 기록 — 트랜잭션 보장. */
    @Transactional
    public void executeUnderLock(UUID reservationId, long executedPrice, long reservedAmount) {
        ReservationEntity reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElse(null);
        if (reservation == null || !reservation.reserved()) return;

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
    }
}