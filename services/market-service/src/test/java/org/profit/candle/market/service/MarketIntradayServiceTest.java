package org.profit.candle.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.profit.candle.market.session.MarketSession;
import org.profit.candle.market.session.TradingCalendar;

@ExtendWith(MockitoExtension.class)
class MarketIntradayServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final TradingCalendar NO_HOLIDAYS = date -> false;

    @Mock
    KiwoomMarketClient kiwoomMarketClient;

    @Test
    void doesNotCacheEmptyKiwoomTickResponse() {
        FakeIntradayTickCache cache = new FakeIntradayTickCache();
        when(kiwoomMarketClient.getTickChart("000660"))
                .thenReturn(response(List.of()))
                .thenReturn(response(List.of(item("+2187000", "20260707151928"))));
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient, cache, marketSession("2026-07-07T10:00:00"));

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
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient, cache, marketSession("2026-07-07T10:00:00"));

        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);
        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);

        verify(kiwoomMarketClient, times(1)).getTickChart("000660");
    }

    @Test
    void returnsRedisCachedTicksBeforeCallingKiwoom() {
        FakeIntradayTickCache cache = new FakeIntradayTickCache();
        cache.ticks = List.of(new IntradayTickResult(2_187_000L, Instant.parse("2026-07-07T06:19:28Z")));
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient, cache, marketSession("2026-07-07T10:00:00"));

        var ticks = service.getIntradayTicks("000660", 0);

        assertThat(ticks).hasSize(1);
        assertThat(ticks.getFirst().price()).isEqualTo(2_187_000L);
        verify(kiwoomMarketClient, times(0)).getTickChart("000660");
    }

    @Test
    void cachesUntilNextOpenWhenMarketIsClosed() {
        FakeIntradayTickCache cache = new FakeIntradayTickCache();
        when(kiwoomMarketClient.getTickChart("000660"))
                .thenReturn(response(List.of(item("+2187000", "20260707151928"))));
        MarketIntradayService service = new MarketIntradayService(
                kiwoomMarketClient,
                cache,
                marketSession("2026-07-03T22:01:00")); // Friday after close (22:00)

        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);

        assertThat(cache.ttls).hasSize(1);
        assertThat(cache.ttls.getFirst()).isGreaterThan(Duration.ofDays(2));
    }

    @Test
    void usesShortCacheDuringRegularMarketHours() {
        FakeIntradayTickCache cache = new FakeIntradayTickCache();
        when(kiwoomMarketClient.getTickChart("000660"))
                .thenReturn(response(List.of(item("+2187000", "20260707151928"))));
        MarketIntradayService service = new MarketIntradayService(
                kiwoomMarketClient,
                cache,
                marketSession("2026-07-07T10:00:00"));

        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);

        assertThat(cache.ttls).containsExactly(Duration.ofMinutes(1));
    }

    private static MarketSession marketSession(String kstDateTime) {
        Instant instant = LocalDateTime.parse(kstDateTime).atZone(KST).toInstant();
        return new MarketSession(Clock.fixed(instant, KST), NO_HOLIDAYS);
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
