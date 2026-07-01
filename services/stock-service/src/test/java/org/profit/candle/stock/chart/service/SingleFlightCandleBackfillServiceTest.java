package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.stock.chart.dto.CandleInterval;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleFlightCandleBackfillServiceTest {

    @Test
    void backfill_sharesConcurrentCallForSameCodeAndInterval() throws Exception {
        DefaultCandleBackfillService delegate = mock(DefaultCandleBackfillService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(delegate.backfill("005930", CandleInterval.DAY_1, 100)).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(1, TimeUnit.SECONDS)).isTrue();
            return 3;
        });
        SingleFlightCandleBackfillService service = new SingleFlightCandleBackfillService(delegate);

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                () -> service.backfill("005930", CandleInterval.DAY_1, 100));
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                () -> service.backfill("005930", CandleInterval.DAY_1, 100));
        release.countDown();

        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo(3);
        verify(delegate, times(1)).backfill("005930", CandleInterval.DAY_1, 100);
    }
}
