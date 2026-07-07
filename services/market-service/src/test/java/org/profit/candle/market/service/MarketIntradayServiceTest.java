package org.profit.candle.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.market.client.KiwoomMarketClient;
import org.profit.candle.market.dto.response.KiwoomTickChartResponse;

@ExtendWith(MockitoExtension.class)
class MarketIntradayServiceTest {

    @Mock
    KiwoomMarketClient kiwoomMarketClient;

    @Test
    void doesNotCacheEmptyKiwoomTickResponse() {
        when(kiwoomMarketClient.getTickChart("000660"))
                .thenReturn(response(List.of()))
                .thenReturn(response(List.of(item("+2187000", "20260707151928"))));
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient);

        assertThat(service.getIntradayTicks("000660", 0)).isEmpty();
        var ticks = service.getIntradayTicks("000660", 0);

        assertThat(ticks).hasSize(1);
        assertThat(ticks.getFirst().price()).isEqualTo(2_187_000L);
        verify(kiwoomMarketClient, times(2)).getTickChart("000660");
    }

    @Test
    void cachesNonEmptyKiwoomTickResponse() {
        when(kiwoomMarketClient.getTickChart("000660"))
                .thenReturn(response(List.of(item("+2187000", "20260707151928"))));
        MarketIntradayService service = new MarketIntradayService(kiwoomMarketClient);

        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);
        assertThat(service.getIntradayTicks("000660", 0)).hasSize(1);

        verify(kiwoomMarketClient, times(1)).getTickChart("000660");
    }

    private static KiwoomTickChartResponse response(List<KiwoomTickChartResponse.Item> ticks) {
        return new KiwoomTickChartResponse("000660", "1", ticks, 0, "OK");
    }

    private static KiwoomTickChartResponse.Item item(String price, String time) {
        return new KiwoomTickChartResponse.Item(price, "1", time, price, price, price, "0", "0");
    }
}
