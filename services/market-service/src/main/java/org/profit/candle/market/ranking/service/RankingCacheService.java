package org.profit.candle.market.ranking.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.dto.cache.RankingSnapshot;
import org.profit.candle.market.ranking.dto.cache.StockRankingCacheItem;
import org.profit.candle.market.ranking.exception.RankingErrorCode;
import org.profit.candle.market.ranking.exception.RankingException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class RankingCacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    public <T> void validateResponse(List<T> items, int returnCode) {
        if (returnCode != 0) {
            throw new RankingException(RankingErrorCode.RANKING_API_ERROR);
        }

        if (items == null || items.isEmpty()) {
            throw new RankingException(RankingErrorCode.EMPTY_RANKING_DATA);
        }
    }

    public <T> List<StockRankingCacheItem> toCacheItems(
            List<T> items,
            Function<T, StockRankingCacheItem> mapper
    ) {
        return items.stream()
                .map(mapper)
                .toList();
    }

    public void save(String redisKey, List<StockRankingCacheItem> items) {
        redisTemplate.opsForValue().set(
                redisKey,
                new RankingSnapshot(items, Instant.now()),
                Duration.ofMinutes(2)
        );
    }

    public RankingSnapshot read(String redisKey) {
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof RankingSnapshot snapshot) {
            return snapshot;
        }
        return null;
    }

    public AtomicInteger rankCounter() {
        return new AtomicInteger(1);
    }

    public long parseLongAbs(String value) {
        return Math.abs(parseLong(value));
    }

    public long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        return Long.parseLong(
                value.replace("+", "")
                        .replace(",", "")
                        .trim()
        );
    }

    public double parseDouble(String value) {
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
}
