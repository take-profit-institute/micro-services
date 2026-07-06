package org.profit.candle.market.publisher;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.dto.message.MarketQuoteMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code market:quotes} 채널 발행.
 *
 * 전용 {@link StringRedisTemplate} + 순수 {@link ObjectMapper} 로 다형성 타입정보(@class) 없는
 * JSON 문자열을 발행한다. wishlist-service 소비 측은 메시지 body 를 그대로 UTF-8 파싱하므로,
 * 기존 GenericJackson(@class 삽입) 템플릿을 재사용하면 안 된다.
 */
@Slf4j
@Component
public class MarketQuotePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String channel;

    public MarketQuotePublisher(
            StringRedisTemplate stringRedisTemplate,
            @Value("${market.quote.channel:market:quotes}") String channel) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.channel = channel;
    }

    public void publish(MarketQuoteMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend(channel, json);
        } catch (RuntimeException e) {
            log.warn("Failed to publish market quote for {}", message.symbol(), e);
        }
    }
}
