package org.profit.candle.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.market.client.KiwoomMarketClient;
import org.profit.candle.market.dto.IntradayTickResult;
import org.profit.candle.market.dto.response.KiwoomTickChartResponse;

@ExtendWith(MockitoExtension.class)
class MarketIntradayServiceTest {

    @Mock
    KiwoomMarketClient kiwoomMarketClient;

    @Test
    void doesNotCacheEmptyKiwoomTickResponse() {
        FakeIntradayTickCache cache = new FakeIntradayTickCache();
        when(kiwoomMarketClient.getTickChart("000660"))
                .thenReturn(response(List.of()))
                .thenReturn(response(List.of(item("+2187000", "20260707151928"))));
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient, cache);

        assertThat(service.getIntradayTicks("000660", 0)).isEmpty();
        var ticks = service.getIntradayTicks("000660", 0);

        assertThat(ticks).hasSize(1);
        assertThat(ticks.getFirst().price()).isEqualTo(2_187_000L);
        assertThat(cache.ticks).hasSize(1);
        verify(kiwoomMarketClient, times(2)).getTickChart("000660");
    }

    @Test
    void cachesNonEmptyKiwoomTickResponse() {
        FakeIntradayTickCache cache = new FakeIntradayTickCache();
        when(kiwoomMarketClient.getTickChart("000660"))
                .thenReturn(response(List.of(item("+2187000", "20260707151928"))));
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient, cache);

        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);
        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);

        verify(kiwoomMarketClient, times(1)).getTickChart("000660");
    }

    @Test
    void returnsRedisCachedTicksBeforeCallingKiwoom() {
        FakeIntradayTickCache cache = new FakeIntradayTickCache();
        cache.ticks = List.of(new IntradayTickResult(2_187_000L, Instant.parse("2026-07-07T06:19:28Z")));
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient, cache);

        var ticks = service.getIntradayTicks("000660", 0);

        assertThat(ticks).hasSize(1);
        assertThat(ticks.getFirst().price()).isEqualTo(2_187_000L);
        verify(kiwoomMarketClient, times(0)).getTickChart("000660");
    }

    private static KiwoomTickChartResponse response(List<KiwoomTickChartResponse.Item> ticks) {
        return new KiwoomTickChartResponse("000660", "1", ticks, 0, "OK");
    }

    private static KiwoomTickChartResponse.Item item(String price, String time) {
        return new KiwoomTickChartResponse.Item(price, "1", time, price, price, price, "0", "0");
    }

    private static final class FakeIntradayTickCache implements IntradayTickCache {
        private List<IntradayTickResult> ticks = List.of();
        private final List<Duration> ttls = new ArrayList<>();

        @Override
        public Optional<List<IntradayTickResult>> get(String symbol) {
            return ticks.isEmpty() ? Optional.empty() : Optional.of(ticks);
        }

        @Override
        public void put(String symbol, List<IntradayTickResult> ticks, Duration ttl) {
            this.ticks = List.copyOf(ticks);
            ttls.add(ttl);
        }
    }
}
