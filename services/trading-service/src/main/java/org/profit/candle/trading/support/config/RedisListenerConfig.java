package org.profit.candle.trading.support.config;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.support.event.MarketQuoteRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * market-service가 발행하는 {@code market:quotes} 채널을 구독하기 위한 Redis 리스너 설정.
 *
 * <p><b>[2026-07-08 현황] 현재 비활성화됨 — {@code @Configuration} 제거.</b> 팀장 결정으로
 * Kafka 경로만 활성화하기로 함. 이 클래스는 삭제하지 않고 유지하되, 컴포넌트 스캔 대상이
 * 아니므로 {@code redisMessageListenerContainer} 빈 자체가 만들어지지 않는다.</p>
 *
 * <p>다시 활성화하려면: 이 클래스에 {@code @Configuration}을 복원하고,
 * {@code MarketQuoteRedisSubscriber}에도 {@code @Component}를 함께 복원해야 한다 — 이 빈 메서드가
 * subscriber를 파라미터로 주입받기 때문에, 둘 중 하나만 켜면 "MarketQuoteRedisSubscriber
 * 빈을 찾을 수 없다"는 에러로 Spring 컨텍스트 시작이 실패한다.</p>
 */
@RequiredArgsConstructor
public class RedisListenerConfig {

    @Value("${market.quote.channel:market:quotes}")
    private String quoteChannel;

    // @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MarketQuoteRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(quoteChannel));
        return container;
    }
}