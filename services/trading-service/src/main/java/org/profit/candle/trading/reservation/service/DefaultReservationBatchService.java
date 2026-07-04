package org.profit.candle.trading.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.trading.client.ChartServiceClient;
import org.profit.candle.trading.client.ChartServiceException;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationSideValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.profit.candle.trading.reservation.event.ReservationDuePayload;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.TradingFeePolicy;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
/**
 * 배치 체결 처리 구현체. DefaultReservationService(사용자 명령)와 분리해
 * 배치 전용 트랜잭션 경계와 로직을 독립적으로 관리한다.
 *
 * <p>구현 범위:</p>
 * <ul>
 *   <li>OPEN+LIMIT: 일별(ProcessOpenLimitReservations) + 건별(ProcessSingleOpenLimitReservation)
 *       두 가지 패턴을 모두 지원한다. 건별은 order의 cancelExpiredPendingOrder 패턴과 동일.</li>
 *   <li>OPEN+MARKET: Market 현재가 Kafka 이벤트 수신 시 즉시 체결</li>
 *   <li>PREV_CLOSE: ChartService.GetPreviousClose(baseDate=오늘) → 전일 종가로 즉시 체결</li>
 *   <li>TODAY_CLOSE: ChartService.GetPreviousClose(baseDate=내일) → 당일 종가로 즉시 체결
 *       (CloseDailyCandles로 종가 확정 후 배치가 호출해야 함)</li>
 *   <li>EXPIRED: 당일 scheduled_date가 지났는데 RESERVED인 예약을 건별로 EXPIRED 처리 + 잔고 반환</li>
 * </ul>
 *
 * <p>건별 트랜잭션이 필요한 작업(락 획득/상태 전이/잔고 선점)은 {@link ReservationBatchExecutor}에
 * 위임한다 — Spring AOP self-invocation 문제를 방지하기 위해 별도 @Service로 분리했다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultReservationBatchService implements ReservationBatchService {

    private final ReservationRepository reservationRepository;
    private final ChartServiceClient chartServiceClient;
    private final OutboxWriter outboxWriter;
    private final ReservationOutboxOperations outboxOperations;
    private final ReservationBatchExecutor batchExecutor;

    @Override
    @Transactional
    public int processOpenLimitReservations(LocalDate targetDate) {
        // 일별 배치 — 전체를 하나의 트랜잭션으로 처리한다.
        // 건별 처리가 필요하면 listOpenLimitReservationIds + processSingleOpenLimitReservation을 사용한다.
        List<ReservationEntity> candidates = reservationRepository
                .findByScheduledDateAndStatusAndTiming(
                        targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.OPEN);

        int count = 0;
        for (ReservationEntity candidate : candidates) {
            if (candidate.getOrderKind() != ReservationOrderKindValue.LIMIT) continue;

            ReservationEntity reservation = reservationRepository
                    .findByIdForUpdate(candidate.getId())
                    .orElse(null);
            if (reservation == null || !reservation.reserved()) continue;

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
                            reservation.getPriceKrw(),
                            reservation.getIdempotencyKey()));
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public void markConverted(UUID reservationId, UUID convertedOrderId) {
        ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getStatus() == ReservationStatusValue.EXECUTED) {
            return;
        }

        reservation.markConverted(convertedOrderId);
        reservationRepository.save(reservation);
    }

    @Override
    public List<UUID> listOpenLimitReservationIds(LocalDate targetDate) {
        // 건별 배치 처리를 위한 대상 id 목록 조회 — order의 findIdsByStatus 패턴과 동일.
        // id만 조회해 엔티티 전체 로딩을 피한다.
        return reservationRepository.findOpenLimitReservationIds(
                targetDate, ReservationStatusValue.RESERVED);
    }

    @Override
    public boolean processSingleOpenLimitReservation(UUID reservationId) {
        // order의 cancelExpiredPendingOrder 패턴과 동일 — 건별 트랜잭션 보장.
        return batchExecutor.processOpenLimitUnderLock(reservationId);
    }

    @Override
    public List<UUID> listStaleConvertingReservationIds(LocalDate targetDate) {
        return reservationRepository.findStaleConvertingReservationIds(targetDate);
    }

    @Override
    public boolean failStaleConvertingReservation(UUID reservationId) {
        return batchExecutor.failConvertingUnderLock(reservationId);
    }

    @Override
    public List<UUID> listExpirableReservationIds(LocalDate targetDate) {
        // EXPIRED 처리 대상 id 목록 조회 — timing 무관, id만 조회.
        return reservationRepository.findExpirableReservationIds(
                targetDate, ReservationStatusValue.RESERVED);
    }

    @Override
    public boolean expireReservation(UUID reservationId) {
        // order의 cancelExpiredPendingOrder 패턴과 동일 — 건별 트랜잭션 보장.
        return batchExecutor.expireUnderLock(reservationId);
    }

    @Override
    public int processOpenMarketReservations(LocalDate targetDate, String symbol, long price) {
        // 5번: price 유효성 검증 — 0/음수면 즉시 거부
        if (price <= 0) {
            log.error("유효하지 않은 현재가 수신 — symbol={}, price={}", symbol, price);
            return 0;
        }

        // 7번: DB에서 symbol/orderKind까지 필터링 — Java stream 필터링 제거
        List<ReservationEntity> candidates = reservationRepository
                .findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                        targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.OPEN,
                        ReservationOrderKindValue.MARKET, symbol);

        int count = 0;
        for (ReservationEntity candidate : candidates) {
            if (!batchExecutor.checkReservedUnderLock(candidate.getId())) continue;

            long reservedAmount = 0;
            if (candidate.getSide() == ReservationSideValue.BUY) {
                BigDecimal amount = BigDecimal.valueOf(price)
                        .multiply(BigDecimal.valueOf(candidate.getQuantity()));
                BigDecimal fee = amount.multiply(TradingFeePolicy.FEE_RATE).setScale(0, RoundingMode.HALF_UP);
                BigDecimal total = amount.add(fee);
                try {
                    reservedAmount = total.longValueExact();
                } catch (ArithmeticException e) {
                    log.error("금액 계산 오버플로 — reservationId={}, amount={}",
                            candidate.getId(), total, e);
                    batchExecutor.markFailedUnderLock(candidate.getId());
                    continue;
                }

                try {
                    batchExecutor.lockBalanceInNewTransaction(candidate.getUserId(), reservedAmount);
                } catch (Exception e) {
                    log.error("잔고 부족으로 체결 실패 — reservationId={}, userId={}, amount={}",
                            candidate.getId(), candidate.getUserId(), reservedAmount, e);
                    batchExecutor.markFailedUnderLock(candidate.getId());
                    continue;
                }
            }

            boolean executed = false;
            try {
                executed = batchExecutor.executeUnderLock(candidate.getId(), price, reservedAmount);
            } finally {
                if (!executed && reservedAmount > 0) {
                    try {
                        batchExecutor.releaseBalanceInNewTransaction(candidate.getUserId(), reservedAmount);
                    } catch (Exception e) {
                        log.error("잔고 보상 실패, 재시도 유도 — reservationId={}, userId={}, amount={}",
                                candidate.getId(), candidate.getUserId(), reservedAmount, e);
                        throw new RuntimeException("잔고 보상 실패 — reservationId: "
                                + candidate.getId(), e);
                    }
                }
            }
            if (executed) count++;
        }
        return count;
    }

    @Override
    public int processPrevCloseReservations(LocalDate targetDate) {
        // PREV_CLOSE: baseDate = targetDate(오늘) → 전일 거래일 종가 조회
        return processCloseReservations(ReservationTimingValue.PREV_CLOSE, targetDate, targetDate);
    }

    @Override
    public int processTodayCloseReservations(LocalDate targetDate) {
        // TODAY_CLOSE: baseDate = targetDate 다음날 → 당일(targetDate) 종가 조회.
        // GetPreviousClose는 baseDate보다 앞선 마지막 일봉을 반환하므로,
        // 내일을 기준으로 넘기면 오늘 확정된 종가가 반환된다.
        // 단, 이 RPC는 CloseDailyCandles로 종가가 확정된 후에 배치가 호출해야 한다.
        return processCloseReservations(ReservationTimingValue.TODAY_CLOSE, targetDate,
                targetDate.plusDays(1));
    }

    /**
     * 종가 체결 공통 로직. 락 보유시간 최소화를 위해 단계별로 처리한다.
     *
     * <pre>
     * 1단계: 락 획득 → RESERVED 확인 → 즉시 커밋 (락 해제)
     * 2단계: 락 없이 종가 조회 (외부 gRPC)
     * 3단계: BUY면 잔고 선점 (REQUIRES_NEW 트랜잭션 — rollback-only 전파 차단)
     * 4단계: 락 재획득 → EXECUTED 전이 → Outbox 기록
     * </pre>
     *
     * 각 단계는 {@link ReservationBatchExecutor}에 위임한다 — self-invocation 방지.
     */
    private int processCloseReservations(ReservationTimingValue timing, LocalDate targetDate,
                                         LocalDate baseDate) {
        List<ReservationEntity> candidates = reservationRepository
                .findByScheduledDateAndStatusAndTiming(
                        targetDate, ReservationStatusValue.RESERVED, timing);

        int count = 0;
        for (ReservationEntity candidate : candidates) {

            // 1단계: 락 획득 → RESERVED 여부 확인 → 즉시 커밋
            if (!batchExecutor.checkReservedUnderLock(candidate.getId())) continue;

            // 2단계: 락 없이 종가 조회
            long executedPrice;
            try {
                executedPrice = chartServiceClient.getPreviousClose(candidate.getSymbol(), baseDate);
            } catch (ChartServiceException e) {
                log.error("종가 조회 실패 — reservationId={}, symbol={}, timing={}",
                        candidate.getId(), candidate.getSymbol(), timing, e);
                batchExecutor.markFailedUnderLock(candidate.getId());
                continue;
            }

            // 3단계: BUY면 잔고 선점 (REQUIRES_NEW — rollback-only 전파 차단)
            long reservedAmount = 0;
            if (candidate.getSide() == ReservationSideValue.BUY) {
                BigDecimal amount = BigDecimal.valueOf(executedPrice)
                        .multiply(BigDecimal.valueOf(candidate.getQuantity()));
                BigDecimal fee = amount.multiply(TradingFeePolicy.FEE_RATE).setScale(0, RoundingMode.HALF_UP);
                BigDecimal total = amount.add(fee);
                try {
                    reservedAmount = total.longValueExact(); // 범위 초과 시 ArithmeticException
                } catch (ArithmeticException e) {
                    log.error("금액 계산 오버플로 — reservationId={}, amount={}",
                            candidate.getId(), total, e);
                    batchExecutor.markFailedUnderLock(candidate.getId());
                    continue;
                }

                try {
                    batchExecutor.lockBalanceInNewTransaction(candidate.getUserId(), reservedAmount);
                } catch (Exception e) {
                    log.error("잔고 부족으로 체결 실패 — reservationId={}, userId={}, amount={}",
                            candidate.getId(), candidate.getUserId(), reservedAmount, e);
                    batchExecutor.markFailedUnderLock(candidate.getId());
                    continue;
                }
            }

            // 4단계: 락 재획득 → EXECUTED 전이 → Outbox 기록
            // lockBalance가 이미 커밋됐으므로 executeUnderLock 실패/no-op 시
            // try-finally로 보상(releaseBalance)을 보장한다 (Qodo #2).
            boolean executed = false;
            try {
                executed = batchExecutor.executeUnderLock(candidate.getId(), executedPrice, reservedAmount);
            } finally {
                if (!executed && reservedAmount > 0) {
                    try {
                        batchExecutor.releaseBalanceInNewTransaction(candidate.getUserId(), reservedAmount);
                    } catch (Exception e) {
                        log.error("잔고 보상 실패, 재시도 유도 — reservationId={}, userId={}, amount={}",
                                candidate.getId(), candidate.getUserId(), reservedAmount, e);
                        throw new RuntimeException("잔고 보상 실패 — reservationId: "
                                + candidate.getId(), e);
                    }
                }
            }
            if (executed) count++;
        }
        return count;
    }
}