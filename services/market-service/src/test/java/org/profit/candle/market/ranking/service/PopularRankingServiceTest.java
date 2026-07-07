package org.profit.candle.market.ranking.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.ranking.client.KiwoomRankingClient;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.dto.response.KiwoomPopularRankItem;
import org.profit.candle.market.ranking.dto.response.KiwoomPopularRankResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

public class PopularRankingServiceTest {

    private final KiwoomRankingClient kiwoomRankingClient = mock(KiwoomRankingClient.class);
    private final RankingCacheService rankingCacheService = mock(RankingCacheService.class);

    private final PopularRankingService popularRankingService =
            new PopularRankingService(kiwoomRankingClient, rankingCacheService);

    @Test
    void refreshSavesPopularRankingToRedis() {

        KiwoomPopularRankItem item = new KiwoomPopularRankItem(
                "005930",
                "삼성전자",
                "1",
                "0",
                "0",
                "70000",
                "2",
                "1.45",
                "20260707",
                "153000"
        );

        KiwoomPopularRankResponse response =
                new KiwoomPopularRankResponse(
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
                        1000L,
                        1.45,
                        "2",
                        1000000L
                )
        );

        when(kiwoomRankingClient.getPopularStocks())
                .thenReturn(response);

        when(rankingCacheService.rankCounter())
                .thenReturn(new AtomicInteger(1));

        when(rankingCacheService.toCacheItems(eq(response.items()), any()))
                .thenReturn(cacheItems);

        popularRankingService.refresh();

        verify(kiwoomRankingClient).getPopularStocks();
        verify(rankingCacheService)
                .validateResponse(response.items(), response.returnCode());
        verify(rankingCacheService)
                .toCacheItems(eq(response.items()), any());
        verify(rankingCacheService)
                .save(StockRankingRedisKey.POPULAR, cacheItems);
    }
}
