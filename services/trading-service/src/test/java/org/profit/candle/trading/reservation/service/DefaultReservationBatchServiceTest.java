package org.profit.candle.trading.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.client.ChartServiceClient;
import org.profit.candle.trading.client.ChartServiceException;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationSideValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.event.OutboxWriter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DefaultReservationBatchService 일별 배치 루프 테스트.
 *
 * <p>건별 트랜잭션(락/상태전이/잔고)은 {@link ReservationBatchExecutor}에 위임돼 있으므로
 * 여기서는 executor를 mock으로 대체하고, 이 클래스가 담당하는 부분만 검증한다:
 * candidate 필터링(orderKind/side), ChartService 실패 시 skip, 금액 오버플로 처리,
 * lockBalance 실패 시 markFailed, executeUnderLock 실패 시 releaseBalance 보상 흐름.</p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultReservationBatchServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ChartServiceClient chartServiceClient;
    @Mock private OutboxWriter outboxWriter;
    @Mock private ReservationOutboxOperations outboxOperations;
    @Mock private ReservationBatchExecutor batchExecutor;

    private DefaultReservationBatchService batchService;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final LocalDate targetDate = LocalDate.of(2026, 7, 6);

    @BeforeEach
    void setUp() {
        batchService = new DefaultReservationBatchService(reservationRepository, chartServiceClient,
                outboxWriter, outboxOperations, batchExecutor);
    }

    private ReservationEntity reservation(ReservationSideValue side, ReservationOrderKindValue orderKind,
                                          Long price, long quantity) {
        return ReservationEntity.reserve(userId, accountId, "005930", side,
                ReservationTimingValue.OPEN, orderKind, quantity, price, targetDate.plusDays(1),
                0L, "idem-" + UUID.randomUUID());
    }

    /** PREV_CLOSE/TODAY_CLOSE 후보 전용 — 이 timing은 AFTER_HOURS_CLOSE만 허용된다. */
    private ReservationEntity closeReservation(ReservationTimingValue timing, ReservationSideValue side,
                                               long quantity) {
        return ReservationEntity.reserve(userId, accountId, "005930", side,
                timing, ReservationOrderKindValue.AFTER_HOURS_CLOSE, quantity, null,
                targetDate.plusDays(1), 0L, "idem-" + UUID.randomUUID());
    }

    @Nested
    @DisplayName("processOpenLimitReservations")
    class ProcessOpenLimitReservations {

        @Test
        void shouldSkipCandidatesWhoseOrderKindIsNotLimit() {
            ReservationEntity marketCandidate = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.MARKET, null, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(
                    targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.OPEN))
                    .thenReturn(List.of(marketCandidate));

            int count = batchService.processOpenLimitReservations(targetDate);

            assertThat(count).isZero();
            // orderKind 필터는 findByIdForUpdate(락) 호출 전에 걸러진다 — 불필요한 락 획득 자체가 없어야 함.
            verify(reservationRepository, never()).findByIdForUpdate(any());
        }

        @Test
        void shouldSkipWhenLockedEntityIsNoLongerReserved() {
            ReservationEntity candidate = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.LIMIT, 70_000L, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(candidate));
            candidate.markCancelled(); // 락 재획득 시점엔 이미 다른 트랜잭션이 취소 처리한 상황 재현
            when(reservationRepository.findByIdForUpdate(candidate.getId()))
                    .thenReturn(Optional.of(candidate));

            int count = batchService.processOpenLimitReservations(targetDate);

            assertThat(count).isZero();
            verifyNoInteractions(outboxWriter);
        }

        @Test
        void shouldConvertEachValidCandidateAndEmitReservationDue() {
            ReservationEntity candidate1 = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.LIMIT, 70_000L, 10);
            ReservationEntity candidate2 = reservation(
                    ReservationSideValue.SELL, ReservationOrderKindValue.LIMIT, 80_000L, 5);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(candidate1, candidate2));
            when(reservationRepository.findByIdForUpdate(candidate1.getId()))
                    .thenReturn(Optional.of(candidate1));
            when(reservationRepository.findByIdForUpdate(candidate2.getId()))
                    .thenReturn(Optional.of(candidate2));

            int count = batchService.processOpenLimitReservations(targetDate);

            assertThat(count).isEqualTo(2);
            assertThat(candidate1.getStatus()).isEqualTo(ReservationStatusValue.CONVERTING);
            assertThat(candidate2.getStatus()).isEqualTo(ReservationStatusValue.CONVERTING);
            verify(outboxWriter, times(2))
                    .record(eq(outboxOperations), eq("ReservationDue"), anyString(), any());
        }
    }

    @Nested
    @DisplayName("markConverted")
    class MarkConverted {

        @Test
        void shouldMarkConvertedWhenNotAlreadyExecuted() {
            ReservationEntity reservation = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.LIMIT, 70_000L, 10);
            reservation.startConverting();
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));
            UUID orderId = UUID.randomUUID();

            batchService.markConverted(reservation.getId(), orderId);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.EXECUTED);
            assertThat(reservation.getConvertedOrderId()).isEqualTo(orderId);
        }

        @Test
        void shouldBeIdempotentWhenAlreadyExecuted() {
            ReservationEntity reservation = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.LIMIT, 70_000L, 10);
            reservation.markExecuted(); // 이미 다른 경로로 완결된 상태(재수신 시나리오)
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            batchService.markConverted(reservation.getId(), UUID.randomUUID());

            // 이미 EXECUTED면 재전이를 시도하지 않고 조용히 반환한다 — save 재호출 없음.
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void shouldThrowNotFoundWhenReservationMissing() {
            UUID reservationId = UUID.randomUUID();
            when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> batchService.markConverted(reservationId, UUID.randomUUID()))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("processOpenMarketReservations")
    class ProcessOpenMarketReservations {

        @Test
        void shouldReturnZeroWithoutQueryingWhenPriceIsInvalid() {
            int count = batchService.processOpenMarketReservations(targetDate, "005930", 0L);

            assertThat(count).isZero();
            verifyNoInteractions(reservationRepository, batchExecutor);
        }

        @Test
        void shouldSkipCandidateWhenNoLongerReservedAtLockAcquisition() {
            ReservationEntity candidate = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.MARKET, null, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                    any(), any(), any(), any(), any())).thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(false);

            int count = batchService.processOpenMarketReservations(targetDate, "005930", 70_000L);

            assertThat(count).isZero();
            verify(batchExecutor, never()).executeUnderLock(any(), anyLong(), anyLong());
        }

        @Test
        void shouldNotLockBalanceForSellCandidates() {
            ReservationEntity candidate = reservation(
                    ReservationSideValue.SELL, ReservationOrderKindValue.MARKET, null, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                    any(), any(), any(), any(), any())).thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(batchExecutor.executeUnderLock(candidate.getId(), 70_000L, 0L)).thenReturn(true);

            int count = batchService.processOpenMarketReservations(targetDate, "005930", 70_000L);

            assertThat(count).isEqualTo(1);
            verify(batchExecutor, never()).lockBalanceInNewTransaction(any(), anyLong());
        }

        @Test
        void shouldMarkFailedAndSkipWhenAmountOverflows() {
            ReservationEntity candidate = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.MARKET, null, Long.MAX_VALUE / 2);
            when(reservationRepository.findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                    any(), any(), any(), any(), any())).thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);

            int count = batchService.processOpenMarketReservations(targetDate, "005930", 3);

            assertThat(count).isZero();
            verify(batchExecutor).markFailedUnderLock(candidate.getId());
            verify(batchExecutor, never()).executeUnderLock(any(), anyLong(), anyLong());
        }

        @Test
        void shouldMarkFailedAndSkipWhenLockBalanceFails() {
            ReservationEntity candidate = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.MARKET, null, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                    any(), any(), any(), any(), any())).thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            doThrow(new RuntimeException("잔고 부족"))
                    .when(batchExecutor).lockBalanceInNewTransaction(eq(candidate.getUserId()), anyLong());

            int count = batchService.processOpenMarketReservations(targetDate, "005930", 70_000L);

            assertThat(count).isZero();
            verify(batchExecutor).markFailedUnderLock(candidate.getId());
            verify(batchExecutor, never()).executeUnderLock(any(), anyLong(), anyLong());
        }

        @Test
        void shouldReleaseBalanceWhenExecuteFailsAfterLockingBalance() {
            ReservationEntity candidate = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.MARKET, null, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                    any(), any(), any(), any(), any())).thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(batchExecutor.executeUnderLock(eq(candidate.getId()), anyLong(), anyLong()))
                    .thenReturn(false); // 락 재획득 시점 경합으로 EXECUTED 전이 실패

            int count = batchService.processOpenMarketReservations(targetDate, "005930", 70_000L);

            assertThat(count).isZero();
            verify(batchExecutor).lockBalanceInNewTransaction(eq(candidate.getUserId()), anyLong());
            verify(batchExecutor).releaseBalanceInNewTransaction(eq(candidate.getUserId()), anyLong());
        }

        @Test
        void shouldPropagateRuntimeExceptionWhenCompensationReleaseFails() {
            ReservationEntity candidate = reservation(
                    ReservationSideValue.BUY, ReservationOrderKindValue.MARKET, null, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                    any(), any(), any(), any(), any())).thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(batchExecutor.executeUnderLock(eq(candidate.getId()), anyLong(), anyLong()))
                    .thenReturn(false);
            doThrow(new RuntimeException("release 실패"))
                    .when(batchExecutor).releaseBalanceInNewTransaction(eq(candidate.getUserId()), anyLong());

            // 잔고 보상까지 실패하면 재시도를 유도하기 위해 예외를 그대로 전파해야 한다.
            assertThatThrownBy(() ->
                    batchService.processOpenMarketReservations(targetDate, "005930", 70_000L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("잔고 보상 실패");
        }

        @Test
        void shouldCountOnlySuccessfullyExecutedCandidates() {
            ReservationEntity succeeds = reservation(
                    ReservationSideValue.SELL, ReservationOrderKindValue.MARKET, null, 10);
            ReservationEntity fails = reservation(
                    ReservationSideValue.SELL, ReservationOrderKindValue.MARKET, null, 5);
            when(reservationRepository.findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
                    any(), any(), any(), any(), any())).thenReturn(List.of(succeeds, fails));
            when(batchExecutor.checkReservedUnderLock(any())).thenReturn(true);
            when(batchExecutor.executeUnderLock(eq(succeeds.getId()), anyLong(), anyLong()))
                    .thenReturn(true);
            when(batchExecutor.executeUnderLock(eq(fails.getId()), anyLong(), anyLong()))
                    .thenReturn(false);

            int count = batchService.processOpenMarketReservations(targetDate, "005930", 70_000L);

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("processPrevCloseReservations / processTodayCloseReservations")
    class ProcessCloseReservations {

        @Test
        void shouldUseTargetDateAsBaseDateForPrevClose() {
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(
                    targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.PREV_CLOSE))
                    .thenReturn(List.of());

            batchService.processPrevCloseReservations(targetDate);

            verifyNoInteractions(chartServiceClient); // 후보가 없으니 종가 조회 자체가 없어야 함
        }

        @Test
        void shouldUseNextDayAsBaseDateForTodayClose() {
            ReservationEntity candidate = closeReservation(
                    ReservationTimingValue.TODAY_CLOSE, ReservationSideValue.SELL, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(
                    targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.TODAY_CLOSE))
                    .thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(chartServiceClient.getPreviousClose("005930", targetDate.plusDays(1)))
                    .thenReturn(70_000L);
            when(batchExecutor.executeUnderLock(eq(candidate.getId()), eq(70_000L), anyLong()))
                    .thenReturn(true);

            batchService.processTodayCloseReservations(targetDate);

            // GetPreviousClose는 baseDate 이전 마지막 종가를 반환하므로, 당일 종가를 얻으려면
            // baseDate를 다음날로 넘겨야 한다 — 이 계약을 정확히 지키는지가 핵심.
            verify(chartServiceClient).getPreviousClose("005930", targetDate.plusDays(1));
        }

        @Test
        void shouldSkipCandidateWhenNoLongerReservedAtLockAcquisition() {
            ReservationEntity candidate = closeReservation(
                    ReservationTimingValue.PREV_CLOSE, ReservationSideValue.BUY, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(false);

            int count = batchService.processPrevCloseReservations(targetDate);

            assertThat(count).isZero();
            verifyNoInteractions(chartServiceClient);
        }

        @Test
        void shouldMarkFailedAndSkipWhenChartServiceThrows() {
            ReservationEntity candidate = closeReservation(
                    ReservationTimingValue.PREV_CLOSE, ReservationSideValue.BUY, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(chartServiceClient.getPreviousClose(eq("005930"), any()))
                    .thenThrow(new ChartServiceException("005930",
                            io.grpc.Status.UNAVAILABLE.asRuntimeException()));

            int count = batchService.processPrevCloseReservations(targetDate);

            assertThat(count).isZero();
            verify(batchExecutor).markFailedUnderLock(candidate.getId());
        }

        @Test
        void shouldNotLockBalanceForSellCandidates() {
            ReservationEntity candidate = closeReservation(
                    ReservationTimingValue.PREV_CLOSE, ReservationSideValue.SELL, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(chartServiceClient.getPreviousClose(eq("005930"), any())).thenReturn(70_000L);
            when(batchExecutor.executeUnderLock(candidate.getId(), 70_000L, 0L)).thenReturn(true);

            int count = batchService.processPrevCloseReservations(targetDate);

            assertThat(count).isEqualTo(1);
            verify(batchExecutor, never()).lockBalanceInNewTransaction(any(), anyLong());
        }

        @Test
        void shouldMarkFailedAndSkipWhenLockBalanceFailsForBuyCandidate() {
            ReservationEntity candidate = closeReservation(
                    ReservationTimingValue.PREV_CLOSE, ReservationSideValue.BUY, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(chartServiceClient.getPreviousClose(eq("005930"), any())).thenReturn(70_000L);
            doThrow(new RuntimeException("잔고 부족"))
                    .when(batchExecutor).lockBalanceInNewTransaction(eq(candidate.getUserId()), anyLong());

            int count = batchService.processPrevCloseReservations(targetDate);

            assertThat(count).isZero();
            verify(batchExecutor).markFailedUnderLock(candidate.getId());
            verify(batchExecutor, never()).executeUnderLock(any(), anyLong(), anyLong());
        }

        @Test
        void shouldReleaseBalanceWhenExecuteFailsAfterLockingBalance() {
            ReservationEntity candidate = closeReservation(
                    ReservationTimingValue.PREV_CLOSE, ReservationSideValue.BUY, 10);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(candidate));
            when(batchExecutor.checkReservedUnderLock(candidate.getId())).thenReturn(true);
            when(chartServiceClient.getPreviousClose(eq("005930"), any())).thenReturn(70_000L);
            when(batchExecutor.executeUnderLock(eq(candidate.getId()), anyLong(), anyLong()))
                    .thenReturn(false);

            int count = batchService.processPrevCloseReservations(targetDate);

            assertThat(count).isZero();
            verify(batchExecutor).releaseBalanceInNewTransaction(eq(candidate.getUserId()), anyLong());
        }

        @Test
        void shouldCountOnlySuccessfullyExecutedCandidates() {
            ReservationEntity succeeds = closeReservation(
                    ReservationTimingValue.PREV_CLOSE, ReservationSideValue.SELL, 10);
            ReservationEntity fails = closeReservation(
                    ReservationTimingValue.PREV_CLOSE, ReservationSideValue.SELL, 5);
            when(reservationRepository.findByScheduledDateAndStatusAndTiming(any(), any(), any()))
                    .thenReturn(List.of(succeeds, fails));
            when(batchExecutor.checkReservedUnderLock(any())).thenReturn(true);
            when(chartServiceClient.getPreviousClose(eq("005930"), any())).thenReturn(70_000L);
            when(batchExecutor.executeUnderLock(eq(succeeds.getId()), anyLong(), anyLong()))
                    .thenReturn(true);
            when(batchExecutor.executeUnderLock(eq(fails.getId()), anyLong(), anyLong()))
                    .thenReturn(false);

            int count = batchService.processPrevCloseReservations(targetDate);

            assertThat(count).isEqualTo(1);
        }
    }
}