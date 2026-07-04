package org.profit.candle.market.ranking.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.client.KiwoomRankingClient;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.dto.response.KiwoomPriceRankItem;
import org.profit.candle.market.ranking.dto.response.KiwoomPriceRankResponse;
import org.profit.candle.market.ranking.dto.response.KiwoomVolumeSpikeResponse;
import org.profit.candle.market.ranking.exception.RankingErrorCode;
import org.profit.candle.market.ranking.exception.RankingException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class StockRankingService {

    private final KiwoomRankingClient kiwoomRankingClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public void refreshRisingRanking() {
        KiwoomPriceRankResponse response = kiwoomRankingClient.getRisingStocks();

        validateResponse(response);

        List<StockRankingCacheItem> rankingItems = toCacheItems(response.items());

        redisTemplate.opsForValue().set(
                StockRankingRedisKey.RISING,
                rankingItems,
                Duration.ofMinutes(2)
        );
}
    public void refreshFallingRanking() {
        KiwoomPriceRankResponse response = kiwoomRankingClient.getFallingStocks();

        validateResponse(response);

        List<StockRankingCacheItem> rankingItems = toCacheItems(response.items());

        redisTemplate.opsForValue().set(
                StockRankingRedisKey.FALLING,
                rankingItems,
                Duration.ofMinutes(2)
        );
    }
    private void validateResponse(KiwoomPriceRankResponse response) {
        if (response == null) {
            throw new RankingException(RankingErrorCode.RANKING_API_ERROR);
        }

        if (response.returnCode() != 0) {
            System.out.println("Ranking API returnCode = " + response.returnCode());
            System.out.println("Ranking API returnMsg = " + response.returnMsg());

            throw new RankingException(RankingErrorCode.RANKING_API_ERROR);
        }

        if (response.items() == null || response.items().isEmpty()) {
            throw new RankingException(RankingErrorCode.EMPTY_RANKING_DATA);
        }
    }

    private List<StockRankingCacheItem> toCacheItems(List<KiwoomPriceRankItem> items) {
        AtomicInteger rank = new AtomicInteger(1);

        return items.stream()
                .map(item -> new StockRankingCacheItem(
                        rank.getAndIncrement(),
                        item.stockCode(),
                        item.stockName(),
                        parseLongAbs(item.currentPrice()),
                        parseLong(item.priceChange()),
                        parseDouble(item.priceChangeRate()),
                        item.priceChangeSign(),
                        parseLong(item.tradingVolume())
                ))
                .toList();
    }
    private long parseLongAbs(String value) {
        return Math.abs(parseLong(value));
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        return Long.parseLong(
                value.replace("+", "")
                        .replace(",", "")
                        .trim()
        );
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }

        return Double.parseDouble(
                value.replace("+", "")
                        .replace("%", "")
                        .replace(",", "")
                        .trim()
        );
    }

    public void refreshVolumeSpikeRanking() {
        KiwoomVolumeSpikeResponse response = kiwoomRankingClient.getVolumeSpikeStocks();

        if (response == null) {
            throw new RankingException(RankingErrorCode.RANKING_API_ERROR);
        }

        if (response.returnCode() != 0) {
            System.out.println("Volume Spike API returnCode = " + response.returnCode());
            System.out.println("Volume Spike API returnMsg = " + response.returnMsg());

            throw new RankingException(RankingErrorCode.RANKING_API_ERROR);
        }

        AtomicInteger rank = new AtomicInteger(1);

        List<StockRankingCacheItem> rankingItems = response.items().stream()
                .map(item -> new StockRankingCacheItem(
                        rank.getAndIncrement(),
                        item.stockCode(),
                        item.stockName(),
                        parseLongAbs(item.currentPrice()),
                        parseLong(item.priceChange()),
                        parseDouble(item.priceChangeRate()),
                        item.priceChangeSign(),
                        parseLong(item.tradingVolume())
                ))
                .toList();

        redisTemplate.opsForValue().set(
                StockRankingRedisKey.VOLUME_SPIKE,
                rankingItems,
                Duration.ofMinutes(2)
        );
    }

}