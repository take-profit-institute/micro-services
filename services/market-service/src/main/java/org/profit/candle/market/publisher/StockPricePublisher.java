package org.profit.candle.market.publisher;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.dto.message.StockPriceMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockPricePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CHANNEL = "stock-price";

    public void publish(StockPriceMessage message) {
        redisTemplate.convertAndSend(CHANNEL, message);
    }
}
