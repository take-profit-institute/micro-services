package org.profit.candle.market.orderbook;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
public class RedisOrderBookPublisher implements OrderBookPublisher {
    private static final String KEY_PREFIX = "market:orderbook:";
    private static final Duration TTL = Duration.ofMinutes(3);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void publish(OrderBookSnapshot snapshot) {
        redisTemplate.opsForValue().set(KEY_PREFIX + snapshot.symbol(), snapshot, TTL);
    }
}
