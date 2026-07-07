package org.profit.candle.market.ranking.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.ranking.client.KiwoomRankingClient;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.dto.response.KiwoomRateRankItem;
import org.profit.candle.market.ranking.dto.response.KiwoomRateRankResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

public class RateRankingServiceTest {

    private final KiwoomRankingClient kiwoomRankingClient = mock(KiwoomRankingClient.class);
    private final RankingCacheService rankingCacheService = mock(RankingCacheService.class);

    private final RateRankingService rateRankingService =
            new RateRankingService(kiwoomRankingClient, rankingCacheService);

    @Test
    void refreshRateUpSavesRateUpRankingToRedis() {
        KiwoomRateRankItem item = new KiwoomRateRankItem(
                "0",
                "005930",
                "삼성전자",
                "70000",
                "2",
                "1000",
                "1.45",
                "100",
                "200",
                "1000000",
                "120.5",
                "10"
        );

        KiwoomRateRankResponse response =
                new KiwoomRateRankResponse(List.of(item), 0, "정상");

        List<StockRankingCacheItem> cacheItems = List.of(
                new StockRankingCacheItem(
                        1,
                        "005930",
                        "삼성전자",
                        70000L,
                        1000L,
                        1.45,
                        "2",
                        1000000L
                )
        );

        when(kiwoomRankingClient.getRateUpStocks()).thenReturn(response);
        when(rankingCacheService.rankCounter()).thenReturn(new AtomicInteger(1));
        when(rankingCacheService.toCacheItems(eq(response.items()), any()))
                .thenReturn(cacheItems);

        rateRankingService.refreshRateUp();

        verify(kiwoomRankingClient).getRateUpStocks();
        verify(rankingCacheService).validateResponse(response.items(), response.returnCode());
        verify(rankingCacheService).toCacheItems(eq(response.items()), any());
        verify(rankingCacheService).save(StockRankingRedisKey.RATE_UP, cacheItems);
    }

    @Test
    void refreshRateDownSavesRateDownRankingToRedis() {
        KiwoomRateRankItem item = new KiwoomRateRankItem(
                "0",
                "005930",
                "삼성전자",
                "69000",
                "5",
                "-1000",
                "-1.43",
                "100",
                "200",
                "1000000",
                "98.5",
                "10"
        );

        KiwoomRateRankResponse response =
                new KiwoomRateRankResponse(List.of(item), 0, "정상");

        List<StockRankingCacheItem> cacheItems = List.of(
                new StockRankingCacheItem(
                        1,
                        "005930",
                        "삼성전자",
                        69000L,
                        -1000L,
                        -1.43,
                        "5",
                        1000000L
                )
        );

        when(kiwoomRankingClient.getRateDownStocks()).thenReturn(response);
        when(rankingCacheService.rankCounter()).thenReturn(new AtomicInteger(1));
        when(rankingCacheService.toCacheItems(eq(response.items()), any()))
                .thenReturn(cacheItems);

        rateRankingService.refreshRateDown();

        verify(kiwoomRankingClient).getRateDownStocks();
        verify(rankingCacheService).validateResponse(response.items(), response.returnCode());
        verify(rankingCacheService).toCacheItems(eq(response.items()), any());
        verify(rankingCacheService).save(StockRankingRedisKey.RATE_DOWN, cacheItems);
    }
}
