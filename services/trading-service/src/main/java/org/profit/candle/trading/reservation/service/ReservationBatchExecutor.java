package org.profit.candle.trading.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.event.ReservationDuePayload;
import org.profit.candle.trading.reservation.event.ReservationExecutedPayload;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 배치 체결의 건별 트랜잭션 실행 단위.
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

    /** FAILED 전이 — 별도 트랜잭션으로 커밋 보장. reservedAmountKrw > 0이면 잔고 반환. */
    @Transactional
    public void markFailedUnderLock(UUID reservationId) {
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(reservation -> {
            if (reservation.reserved()) {
                if (reservation.getReservedAmountKrw() > 0) {
                    accountService.releaseBalance(reservation.getUserId(),
                            reservation.getReservedAmountKrw());
                }
                reservation.markFailed();
                reservationRepository.save(reservation);
            }
        });
    }

    /**
     * EXPIRED 전이 — order의 cancelExpiredPendingOrder 패턴과 동일.
     * RESERVED → EXPIRED 전이 + reservedAmountKrw > 0이면 releaseBalance().
     *
     * @return 처리 성공 여부 (false면 이미 RESERVED 아님)
     */
    @Transactional
    public boolean expireUnderLock(UUID reservationId) {
        ReservationEntity reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElse(null);
        if (reservation == null || !reservation.reserved()) return false;

        if (reservation.getReservedAmountKrw() > 0) {
            accountService.releaseBalance(reservation.getUserId(),
                    reservation.getReservedAmountKrw());
        }
        reservation.markExpired();
        reservationRepository.save(reservation);
        return true;
    }

    /**
     * 건별 OPEN+LIMIT 처리 — order의 cancelExpiredPendingOrder 패턴과 동일.
     * RESERVED → CONVERTING 전이 + ReservationDue Outbox 기록.
     *
     * @return 처리 성공 여부 (false면 이미 RESERVED 아님)
     */
    @Transactional
    public boolean processOpenLimitUnderLock(UUID reservationId) {
        ReservationEntity reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElse(null);
        if (reservation == null || !reservation.reserved()) return false;
        if (reservation.getOrderKind() != ReservationOrderKindValue.LIMIT) return false;

        reservation.startConverting();
        reservationRepository.save(reservation);

        outboxWriter.record(outboxOperations, "ReservationDue",
                reservation.getId().toString(),
                new ReservationDuePayload(
                        reservation.getId().toString(),
                        reservation.getUserId().toString(),
                        reservation.getAccountId().toString(),
                        reservation.getSymbol(),
                        reservation.getSide().name(),
                        reservation.getQuantity(),
                        reservation.getPriceKrw() == null ? 0L : reservation.getPriceKrw(),
                        reservation.getReservedAmountKrw(),
                        reservation.getIdempotencyKey()));
        return true;
    }

    /**
     * CONVERTING 타임아웃 처리 — 당일 15:30 이후에도 CONVERTING 상태인 예약을 FAILED로 전이.
     * ReservationDue 이벤트가 유실되거나 order_svc가 처리 실패한 케이스를 정리한다.
     * reservedAmountKrw > 0이면 releaseBalance()로 잔고 반환.
     *
     * @return 처리 성공 여부 (false면 이미 CONVERTING 아님)
     */
    @Transactional
    public boolean failConvertingUnderLock(UUID reservationId) {
        ReservationEntity reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElse(null);
        if (reservation == null
                || reservation.getStatus() != ReservationStatusValue.CONVERTING) return false;

        if (reservation.getReservedAmountKrw() > 0) {
            accountService.releaseBalance(reservation.getUserId(),
                    reservation.getReservedAmountKrw());
        }
        reservation.markFailed();
        reservationRepository.save(reservation);
        return true;
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