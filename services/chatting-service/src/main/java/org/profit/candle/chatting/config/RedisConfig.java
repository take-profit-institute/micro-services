package org.profit.candle.chatting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

/**
 * Redis Pub/Sub 구독용 리액티브 리스너 컨테이너.
 *
 * <p>{@code ReactiveStringRedisTemplate}(카운터/PUBLISH용)와
 * {@code ReactiveRedisConnectionFactory}는 Spring Boot가 자동 구성한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }
}
