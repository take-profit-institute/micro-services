package org.profit.candle.chatting.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis Pub/Sub 기반 팬아웃 구현.
 *
 * <p>Pub/Sub은 fire-and-forget이라 끊긴 동안의 메시지는 유실된다(메시지 비영속 설계상 의도된 동작).
 */
@Component
@RequiredArgsConstructor
public class RedisChatBroker implements ChatBroker {

    private final ReactiveRedisMessageListenerContainer listener;
    private final ReactiveStringRedisTemplate redis;

    @Override
    public Flux<String> subscribe(String channel) {
        return listener.receive(new ChannelTopic(channel))
                .map(ReactiveSubscription.Message::getMessage);
    }

    @Override
    public Mono<Long> publish(String channel, String payload) {
        return redis.convertAndSend(channel, payload);
    }
}
