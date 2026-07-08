package org.profit.candle.market.orderbook;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderBookCacheService {
    private static final String KEY_PREFIX = "market:orderbook:";

    private final RedisTemplate<String, Object> redisTemplate;

    public Optional<OrderBookSnapshot> find(String symbol) {
        Object cached = redisTemplate.opsForValue().get(key(symbol));
        if (cached instanceof OrderBookSnapshot snapshot) {
            return Optional.of(snapshot);
        }
        return Optional.empty();
    }

    private static String key(String symbol) {
        return KEY_PREFIX + symbol;
    }
}
