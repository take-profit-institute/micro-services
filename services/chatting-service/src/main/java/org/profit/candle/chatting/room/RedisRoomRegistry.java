package org.profit.candle.chatting.room;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis 기반 방 레지스트리 — 배정({@link RoomAssigner})과 인원 카운팅({@link RoomCounter})을 함께 구현한다.
 * 소비자는 필요한 인터페이스 하나만 주입받는다(REST는 배정, WS는 카운팅).
 *
 * <h3>presence 모델 — per-멤버 TTL(자가치유)</h3>
 * 단일 INCR/DECR 카운터는 비정상 종료(DECR 누락)나 재연결(중복 INCR)로 영구히 드리프트한다. 대신
 * 방마다 ZSET({@code {roomId}_presence})을 두어 member=커넥션, score=마지막 heartbeat 시각으로 관리한다.
 * 활성 인원 = score가 {@code now - presenceTtl} 이후인 멤버 수. 조회 전 만료분을 정리하므로, leave가
 * 누락돼도 heartbeat가 끊긴 멤버는 TTL 경과 후 자동으로 빠진다.
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
                    .concatMap(room -> activeCount(new RoomKey(symbol, room))
                            .map(current -> RoomAssignment.of(new RoomKey(symbol, room), current)))
                    .filter(assignment -> assignment.count() < properties.room().capacity())
                    .next()
                    .switchIfEmpty(Mono.defer(() -> allocateNewRoom(symbol)));
        });
    }

    @Override
    public Mono<Long> enter(RoomKey key, String memberId) {
        return touch(key, memberId);
    }

    @Override
    public Mono<Long> heartbeat(RoomKey key, String memberId) {
        return touch(key, memberId);
    }

    @Override
    public Mono<Long> leave(RoomKey key, String memberId) {
        return redis.opsForZSet().remove(key.presenceKey(), memberId)
                .then(activeCount(key));
    }

    /** presence에 멤버 등록/갱신(ZADD score=now) + 키 TTL 안전망 → 정리 후 활성 인원 반환. */
    private Mono<Long> touch(RoomKey key, String memberId) {
        String presenceKey = key.presenceKey();
        double now = Instant.now().toEpochMilli();
        return redis.opsForZSet().add(presenceKey, memberId, now)
                // 방이 완전히 비면(모든 멤버 만료) 메모리 회수되도록 키 자체에도 넉넉한 TTL을 건다.
                .then(redis.expire(presenceKey, properties.room().presenceTtl().multipliedBy(2)))
                .then(activeCount(key));
    }

    /** 만료(score < now - presenceTtl) 멤버를 정리한 뒤 활성 인원(ZCARD)을 센다. */
    private Mono<Long> activeCount(RoomKey key) {
        String presenceKey = key.presenceKey();
        double cutoff = Instant.now().toEpochMilli() - properties.room().presenceTtl().toMillis();
        return redis.opsForZSet().removeRangeByScore(presenceKey, Range.closed(0d, cutoff))
                .then(redis.opsForZSet().size(presenceKey))
                .map(size -> size == null ? 0L : size)
                .defaultIfEmpty(0L);
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
}
