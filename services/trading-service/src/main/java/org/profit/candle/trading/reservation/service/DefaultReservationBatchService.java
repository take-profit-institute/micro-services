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
 *   <li>OPEN+LIMIT: ReservationDue Kafka 이벤트 발행 → order_svc가 Order 생성 (Option C)</li>
 *   <li>PREV_CLOSE: ChartService.GetPreviousClose(baseDate=오늘) → 전일 종가로 즉시 체결</li>
 *   <li>TODAY_CLOSE: ChartService.GetPreviousClose(baseDate=내일) → 당일 종가로 즉시 체결
 *       (CloseDailyCandles로 종가 확정 후 배치가 호출해야 함)</li>
 *   <li>OPEN+MARKET: Market 도메인 현재가 스키마 확정 후 추가 예정</li>
 * </ul>
 *
 * <p>건별 트랜잭션이 필요한 작업(락 획득/상태 전이/잔고 선점)은 {@link ReservationBatchExecutor}에
 * 위임한다 — Spring AOP self-invocation 문제를 방지하기 위해 별도 @Service로 분리했다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultReservationBatchService implements ReservationBatchService {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.00015");

    private final ReservationRepository reservationRepository;
    private final ChartServiceClient chartServiceClient;
    private final OutboxWriter outboxWriter;
    private final ReservationOutboxOperations outboxOperations;
    private final ReservationBatchExecutor batchExecutor;

    @Override
    @Transactional
    public int processOpenLimitReservations(LocalDate targetDate) {
        // ── 트랜잭션 설계 결정 ────────────────────────────────────────────
        // 현재: @Transactional 하나로 당일 OPEN+LIMIT 전체를 묶음.
        // 한 건이라도 예외가 나면 전체 롤백되는 리스크가 있다.
        //
        // 이 구조를 선택한 이유:
        //   - startConverting()이 실패하는 케이스는 !reservation.reserved()뿐이고,
        //     루프 진입 전 이미 continue로 걸러냄.
        //   - save()/outboxWriter.record() 실패 시 해당 건은 RESERVED 상태로 남아
        //     다음 배치 실행에서 재처리된다 — 실질적 전체 롤백 리스크가 낮음.
        //   - 7/9 발표 일정상 단순 구조를 우선함.
        //
        // 개선 방안 (발표 후 리팩토링 이슈로 고려):
        //
        // [방안 A] 배치 담당자가 건별로 gRPC 호출하도록 proto 변경 (권장)
        //   - ProcessOpenLimitReservationsRequest의 scheduled_date 대신 reservation_id를 받음.
        //   - 배치 서비스가 대상 목록을 조회한 뒤 reservation_id마다 RPC를 따로 호출.
        //   - order의 ExpirePendingOrders/cancelExpiredPendingOrder와 완전히 동일한 패턴.
        //   - trading-service 코드 변경 없이 proto + 배치 서비스만 수정하면 됨.
        //   - 단점: 배치 담당자 협의 필요, RPC 호출 횟수 증가(건수만큼).
        //
        // [방안 B] 건별 트랜잭션을 위한 Helper 클래스 분리
        //   → 이미 processCloseReservations에서 ReservationBatchExecutor로 구현됨.
        //      OPEN+LIMIT도 동일하게 리팩토링 가능.
        // ─────────────────────────────────────────────────────────────────
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
    public int processOpenMarketReservations(LocalDate targetDate, String symbol, long price) {
        // OPEN+MARKET: Kafka로 수신한 현재가로 즉시 체결.
        // 해당 종목의 당일 OPEN+MARKET RESERVED 예약만 처리한다.
        List<ReservationEntity> candidates = reservationRepository
                .findByScheduledDateAndStatusAndTiming(
                        targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.OPEN)
                .stream()
                .filter(r -> r.getOrderKind() == ReservationOrderKindValue.MARKET
                        && r.getSymbol().equals(symbol))
                .toList();

        int count = 0;
        for (ReservationEntity candidate : candidates) {
            if (!batchExecutor.checkReservedUnderLock(candidate.getId())) continue;

            long reservedAmount = 0;
            if (candidate.getSide() == ReservationSideValue.BUY) {
                BigDecimal amount = BigDecimal.valueOf(price)
                        .multiply(BigDecimal.valueOf(candidate.getQuantity()));
                BigDecimal fee = amount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);
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
                        log.error("잔고 보상 실패 — reservationId={}, userId={}, amount={}",
                                candidate.getId(), candidate.getUserId(), reservedAmount, e);
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
                BigDecimal fee = amount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);
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
                        log.error("잔고 보상 실패 — reservationId={}, userId={}, amount={}",
                                candidate.getId(), candidate.getUserId(), reservedAmount, e);
                    }
                }
            }
            if (executed) count++;
        }
        return count;
    }
}