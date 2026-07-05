package org.profit.candle.batch.trading.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.profit.candle.batch.trading.client.DailyCandleCloseClient;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.exception.TradingBatchErrorCode;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class TradingTodayCloseTaskletTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);
    private static final String IDEMPOTENCY_KEY = "stock-close-daily:2026-07-06";

    @Test
    void processesReservationsOnlyAfterDailyCandlesClose() {
        DailyCandleCloseClient candleClient = mock(DailyCandleCloseClient.class);
        TradingBatchClient tradingClient = mock(TradingBatchClient.class);
        when(candleClient.close(DATE, IDEMPOTENCY_KEY)).thenReturn(20);
        when(tradingClient.processTodayCloseReservations(DATE)).thenReturn(5);
        TradingTodayCloseTasklet tasklet = tasklet(candleClient, tradingClient);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        InOrder order = inOrder(candleClient, tradingClient);
        order.verify(candleClient).close(DATE, IDEMPOTENCY_KEY);
        order.verify(tradingClient).processTodayCloseReservations(DATE);
    }

    @Test
    void doesNotProcessReservationsWhenDailyCloseFailsThreeTimes() {
        DailyCandleCloseClient candleClient = mock(DailyCandleCloseClient.class);
        TradingBatchClient tradingClient = mock(TradingBatchClient.class);
        when(candleClient.close(DATE, IDEMPOTENCY_KEY)).thenThrow(retryableFailure());
        TradingTodayCloseTasklet tasklet = tasklet(candleClient, tradingClient);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(TradingBatchException.class);
        verify(candleClient, times(3)).close(DATE, IDEMPOTENCY_KEY);
        verify(tradingClient, never()).processTodayCloseReservations(DATE);
    }

    @Test
    void retriesTradingWithoutClosingCandlesAgain() {
        DailyCandleCloseClient candleClient = mock(DailyCandleCloseClient.class);
        TradingBatchClient tradingClient = mock(TradingBatchClient.class);
        when(candleClient.close(DATE, IDEMPOTENCY_KEY)).thenReturn(20);
        when(tradingClient.processTodayCloseReservations(DATE))
                .thenThrow(new TradingBatchException(
                        TradingBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE,
                        new IllegalStateException("temporary failure")
                ))
                .thenReturn(5);
        TradingTodayCloseTasklet tasklet = tasklet(candleClient, tradingClient);

        tasklet.execute(null, null);

        verify(candleClient).close(DATE, IDEMPOTENCY_KEY);
        verify(tradingClient, times(2)).processTodayCloseReservations(DATE);
    }

    private TradingTodayCloseTasklet tasklet(
            DailyCandleCloseClient candleClient,
            TradingBatchClient tradingClient
    ) {
        return new TradingTodayCloseTasklet(
                DATE,
                candleClient,
                tradingClient,
                new TradingBatchRetryExecutor()
        );
    }

    private TradingBatchException retryableFailure() {
        return new TradingBatchException(
                TradingBatchErrorCode.STOCK_CLIENT_RETRYABLE,
                new IllegalStateException("temporary failure")
        );
    }
}
