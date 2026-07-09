package org.profit.candle.market.ranking.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.ranking.client.KiwoomRankingClient;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.dto.response.KiwoomVolumeSpikeItem;
import org.profit.candle.market.ranking.dto.response.KiwoomVolumeSpikeResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VolumeSpikeRankingServiceTest {

    private final KiwoomRankingClient kiwoomRankingClient = mock(KiwoomRankingClient.class);
    private final RankingCacheService rankingCacheService = mock(RankingCacheService.class);

    private final VolumeSpikeRankingService volumeSpikeRankingService =
            new VolumeSpikeRankingService(kiwoomRankingClient, rankingCacheService);

    @Test
    void refreshSavesVolumeSpikeRankingToRedis() {
        KiwoomVolumeSpikeItem item = new KiwoomVolumeSpikeItem(
                "005930",
                "삼성전자",
                "+70000",
                "2",
                "+1000",
                "+1.45",
                "500000",
                "1000000",
                "500000",
                "100.00"
        );

        KiwoomVolumeSpikeResponse response =
                new KiwoomVolumeSpikeResponse(
                        List.of(item),
                        0,
                        "정상적으로 처리되었습니다"
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

        when(kiwoomRankingClient.getVolumeSpikeStocks())
                .thenReturn(response);

        when(rankingCacheService.rankCounter())
                .thenReturn(new AtomicInteger(1));

        when(rankingCacheService.toCacheItems(eq(response.items()), any()))
                .thenReturn(cacheItems);

        volumeSpikeRankingService.refresh();

        verify(kiwoomRankingClient).getVolumeSpikeStocks();
        verify(rankingCacheService).validateResponse(response.items(), response.returnCode());
        verify(rankingCacheService).toCacheItems(eq(response.items()), any());
        verify(rankingCacheService).save(StockRankingRedisKey.VOLUME_SPIKE, cacheItems);
    }

}
