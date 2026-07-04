package org.profit.candle.market.ranking.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.client.KiwoomRankingClient;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.dto.response.KiwoomPopularRankResponse;
import org.profit.candle.market.ranking.exception.RankingErrorCode;
import org.profit.candle.market.ranking.exception.RankingException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class PopularRankingService {

    private final KiwoomRankingClient kiwoomRankingClient;
    private final RankingCacheService rankingCacheService;

    public void refresh() {
        KiwoomPopularRankResponse response = kiwoomRankingClient.getPopularStocks();

        validateResponse(response);

        AtomicInteger rank = rankingCacheService.rankCounter();

        List<StockRankingCacheItem> rankingItems =
                rankingCacheService.toCacheItems(response.items(), item ->
                        new StockRankingCacheItem(
                                rank.getAndIncrement(),
                                item.stockCode(),
                                item.stockName(),
                                rankingCacheService.parseLongAbs(item.currentPrice()),
                                0L,
                                rankingCacheService.parseDouble(item.priceChangeRate()),
                                item.priceChangeSign(),
                                0L
                        )
                );

        rankingCacheService.save(StockRankingRedisKey.POPULAR, rankingItems);
    }

    private void validateResponse(KiwoomPopularRankResponse response) {
        System.out.println("========== 인기순 응답 ==========");
        System.out.println(response);

        if (response == null) {
            throw new RankingException(RankingErrorCode.RANKING_API_ERROR);
        }

        System.out.println("returnCode = " + response.returnCode());
        System.out.println("message = " + response.returnMsg());

        rankingCacheService.validateResponse(
                response.items(),
                response.returnCode()
        );
    }
}
