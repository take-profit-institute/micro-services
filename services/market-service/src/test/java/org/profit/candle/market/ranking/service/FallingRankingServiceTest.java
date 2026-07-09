package org.profit.candle.market.ranking.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.ranking.client.KiwoomRankingClient;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.dto.response.KiwoomPriceRankItem;
import org.profit.candle.market.ranking.dto.response.KiwoomPriceRankResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

public class FallingRankingServiceTest {

    private final KiwoomRankingClient kiwoomRankingClient = mock(KiwoomRankingClient.class);
    private final RankingCacheService rankingCacheService = mock(RankingCacheService.class);

    private final FallingRankingService fallingRankingService =
            new FallingRankingService(kiwoomRankingClient, rankingCacheService);

    @Test
    void refreshSavesFallingRankingToRedis() {

        KiwoomPriceRankItem item = new KiwoomPriceRankItem(
                "005930",
                "삼성전자",
                "70000",
                "-1000",
                "-1.45",
                "1000000",
                "5"
        );

        KiwoomPriceRankResponse response =
                new KiwoomPriceRankResponse(
                        List.of(item),
                        0,
                        "정상"
                );

        List<StockRankingCacheItem> cacheItems = List.of(
                new StockRankingCacheItem(
                        1,
                        "005930",
                        "삼성전자",
                        70000L,
                        -1000L,
                        -1.45,
                        "5",
                        1000000L
                )
        );

        when(kiwoomRankingClient.getFallingStocks())
                .thenReturn(response);

        when(rankingCacheService.rankCounter())
                .thenReturn(new AtomicInteger(1));

        when(rankingCacheService.toCacheItems(eq(response.items()), any()))
                .thenReturn(cacheItems);

        fallingRankingService.refresh();

        verify(kiwoomRankingClient).getFallingStocks();
        verify(rankingCacheService)
                .validateResponse(response.items(), response.returnCode());
        verify(rankingCacheService)
                .toCacheItems(eq(response.items()), any());
        verify(rankingCacheService)
                .save(StockRankingRedisKey.FALLING, cacheItems);
    }
}
