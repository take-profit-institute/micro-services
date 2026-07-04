package org.profit.candle.market.ranking.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.client.KiwoomRankingClient;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.dto.response.KiwoomVolumeSpikeResponse;
import org.profit.candle.market.ranking.exception.RankingErrorCode;
import org.profit.candle.market.ranking.exception.RankingException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class VolumeSpikeRankingService {

    private final KiwoomRankingClient kiwoomRankingClient;
    private final RankingCacheService rankingCacheService;

    public void refresh() {
        KiwoomVolumeSpikeResponse response = kiwoomRankingClient.getVolumeSpikeStocks();

        validateResponse(response);

        AtomicInteger rank = rankingCacheService.rankCounter();

        List<StockRankingCacheItem> rankingItems =
                rankingCacheService.toCacheItems(response.items(), item ->
                        new StockRankingCacheItem(
                                rank.getAndIncrement(),
                                item.stockCode(),
                                item.stockName(),
                                rankingCacheService.parseLongAbs(item.currentPrice()),
                                rankingCacheService.parseLong(item.priceChange()),
                                rankingCacheService.parseDouble(item.priceChangeRate()),
                                item.priceChangeSign(),
                                rankingCacheService.parseLong(item.tradingVolume())
                        )
                );

        rankingCacheService.save(StockRankingRedisKey.VOLUME_SPIKE, rankingItems);
    }

    private void validateResponse(KiwoomVolumeSpikeResponse response) {
        if (response == null) {
            throw new RankingException(RankingErrorCode.RANKING_API_ERROR);
        }

        rankingCacheService.validateResponse(
                response.items(),
                response.returnCode()
        );
    }
}
