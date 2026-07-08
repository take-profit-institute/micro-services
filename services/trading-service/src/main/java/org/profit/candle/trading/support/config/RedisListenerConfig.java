package org.profit.candle.trading.support.config;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.support.event.MarketQuoteRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * market-service가 발행하는 {@code market:quotes} 채널을 구독하기 위한 Redis 리스너 설정.
 * wishlist-service의 {@code RedisListenerConfig}와 동일한 채널·구조를 사용한다.
 *
 * <p>trading-service build.gradle에 {@code spring-boot-starter-data-redis} 의존성이
 * 필요하다. application.yml에 {@code spring.data.redis.host/port}와
 * {@code market.quote.channel}(기본값 {@code market:quotes})을 설정한다.</p>
 */
@Configuration
@RequiredArgsConstructor
public class RedisListenerConfig {

    @Value("${market.quote.channel:market:quotes}")
    private String quoteChannel;

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MarketQuoteRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(quoteChannel));
        return container;
    }
}