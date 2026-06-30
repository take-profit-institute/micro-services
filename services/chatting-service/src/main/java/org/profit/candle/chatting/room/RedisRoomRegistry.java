package org.profit.candle.chatting.room;

import lombok.RequiredArgsConstructor;
import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis 기반 방 레지스트리 — 배정({@link RoomAssigner})과 인원 카운팅({@link RoomCounter})을 함께 구현한다.
 * 소비자는 필요한 인터페이스 하나만 주입받는다(REST는 배정, WS는 카운팅).
 *
 * <h3>동시성 — 낙관적(락 없음)</h3>
 * 버스트 시 같은 방에 순간적으로 정원을 넘겨 배정될 수 있으나, 주식 채팅 특성상 "대략 500명 안팎"이면
 * 충분하다는 결정에 따라 분산 락을 쓰지 않는다. 엄격 보장이 필요해지면 배정 로직에 Redisson 락을 도입한다.
 */
@Component
@RequiredArgsConstructor
public class RedisRoomRegistry implements RoomAssigner, RoomCounter {

    /** 종목별 현재 발급된 최대 방 번호. */
    private static final String ROOM_SEQ_PREFIX = "chat:rooms:";

    private final ReactiveStringRedisTemplate redis;
    private final ChatProperties properties;

    @Override
    public Mono<RoomAssignment> assign(String symbol) {
        return maxRoom(symbol).flatMap(max -> {
            if (max == 0) {
                return allocateNewRoom(symbol);
            }
            return Flux.range(1, max)
                    .concatMap(room -> count(new RoomKey(symbol, room))
                            .map(current -> RoomAssignment.of(new RoomKey(symbol, room), current)))
                    .filter(assignment -> assignment.count() < properties.room().capacity())
                    .next()
                    .switchIfEmpty(allocateNewRoom(symbol));
        });
    }

    @Override
    public Mono<Long> enter(RoomKey key) {
        return redis.opsForValue().increment(key.countKey())
                .flatMap(value -> redis.expire(key.countKey(), properties.room().counterTtl()).thenReturn(value));
    }

    @Override
    public Mono<Long> leave(RoomKey key) {
        return redis.opsForValue().decrement(key.countKey());
    }

    private Mono<RoomAssignment> allocateNewRoom(String symbol) {
        return redis.opsForValue().increment(ROOM_SEQ_PREFIX + symbol)
                .map(room -> RoomAssignment.of(new RoomKey(symbol, room.intValue()), 0L));
    }

    private Mono<Integer> maxRoom(String symbol) {
        return redis.opsForValue().get(ROOM_SEQ_PREFIX + symbol)
                .map(Integer::parseInt)
                .defaultIfEmpty(0);
    }

    private Mono<Long> count(RoomKey key) {
        return redis.opsForValue().get(key.countKey())
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }
}
