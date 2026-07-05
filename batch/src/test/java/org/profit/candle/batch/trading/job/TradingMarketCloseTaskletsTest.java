package org.profit.candle.batch.trading.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.exception.TradingBatchErrorCode;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class TradingMarketCloseTaskletsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    /** 미체결 주문 만료 RPC의 처리 완료 여부를 검증한다. */
    @Test
    void expiresPendingOrders() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.expirePendingOrders()).thenReturn(7);
        TradingExpirePendingOrdersTasklet tasklet =
                new TradingExpirePendingOrdersTasklet(client, retryExecutor());

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(client).expirePendingOrders();
    }

    /** stale 목록을 모두 호출하고 false 응답은 정상적으로 건너뛴다. */
    @Test
    void failsStaleConvertingReservationsAndSkipsChangedState() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.listStaleConvertingReservations(DATE))
                .thenReturn(List.of("reservation-1", "reservation-2"));
        when(client.failStaleConvertingReservation("reservation-1")).thenReturn(true);
        when(client.failStaleConvertingReservation("reservation-2")).thenReturn(false);
        TradingFailStaleConvertingTasklet tasklet =
                new TradingFailStaleConvertingTasklet(DATE, client, retryExecutor());

        tasklet.execute(null, null);

        verify(client).failStaleConvertingReservation("reservation-1");
        verify(client).failStaleConvertingReservation("reservation-2");
    }

    /** stale 건별 처리의 일시적 오류가 해당 예약만 재시도되는지 검증한다. */
    @Test
    void retriesOnlyFailedStaleReservation() {
        TradingBatchClient client = mock(TradingBatchClient.class);
        when(client.listStaleConvertingReservations(DATE))
                .thenReturn(List.of("reservation-1"));
        when(client.failStaleConvertingReservation("reservation-1"))
                .thenThrow(retryableFailure())
                .thenReturn(true);
        TradingFailStaleConvertingTasklet tasklet =
                new TradingFailStaleConvertingTasklet(DATE, client, retryExecutor());

        tasklet.execute(null, null);

        verify(client, times(2)).failStaleConvertingReservation("reservation-1");
    }

    /** 실제 최대 3회 재시도 정책을 생성한다. */
    private TradingBatchRetryExecutor retryExecutor() {
        return new TradingBatchRetryExecutor();
    }

    /** 재시도 가능한 Trading 외부 오류를 생성한다. */
    private TradingBatchException retryableFailure() {
        return new TradingBatchException(
                TradingBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE,
                new IllegalStateException("temporary failure")
        );
    }
}
