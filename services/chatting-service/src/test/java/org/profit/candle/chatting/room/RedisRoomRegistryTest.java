package org.profit.candle.chatting.room;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRoomRegistryTest {

    ReactiveStringRedisTemplate redis;
    ReactiveValueOperations<String, String> values;
    ReactiveZSetOperations<String, String> zset;
    RedisRoomRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        values = mock(ReactiveValueOperations.class);
        zset = mock(ReactiveZSetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zset);

        registry = new RedisRoomRegistry(redis, new ChatProperties(
                new ChatProperties.Jwt("http://unused/.well-known/jwks.json", "candle-auth-test", "candle-api"),
                new ChatProperties.Room(2, Duration.ofMinutes(1), Duration.ofSeconds(20)),
                new ChatProperties.Cors(List.of())));
    }

    @Test
    void assign_noExistingRoom_allocatesFirstRoom() {
        when(values.get("chat:rooms:005930")).thenReturn(Mono.empty());
        when(values.increment("chat:rooms:005930")).thenReturn(Mono.just(1L));

        StepVerifier.create(registry.assign("005930"))
                .expectNext(new RoomAssignment("005930", 1, "005930_1", "chat:005930_1", 0L))
                .verifyComplete();
    }

    @Test
    void assign_existingRoomUnderCapacity_returnsExistingRoomWithActiveCount() {
        when(values.get("chat:rooms:005930")).thenReturn(Mono.just("2"));
        // 각 방 presence ZSET: 만료 정리 후 ZCARD로 활성 인원을 센다.
        stubActiveCount("005930_1_presence", 2L);
        stubActiveCount("005930_2_presence", 1L);

        StepVerifier.create(registry.assign("005930"))
                .expectNext(new RoomAssignment("005930", 2, "005930_2", "chat:005930_2", 1L))
                .verifyComplete();
    }

    @Test
    void assign_allRoomsFull_allocatesNewRoom() {
        when(values.get("chat:rooms:005930")).thenReturn(Mono.just("2"));
        stubActiveCount("005930_1_presence", 2L);
        stubActiveCount("005930_2_presence", 2L);
        when(values.increment("chat:rooms:005930")).thenReturn(Mono.just(3L));

        StepVerifier.create(registry.assign("005930"))
                .expectNext(new RoomAssignment("005930", 3, "005930_3", "chat:005930_3", 0L))
                .verifyComplete();
    }

    @Test
    void enter_registersMemberAndReturnsActiveCount() {
        RoomKey key = new RoomKey("005930", 1);
        when(zset.add(eq("005930_1_presence"), eq("m1"), any(Double.class))).thenReturn(Mono.just(true));
        when(redis.expire(eq("005930_1_presence"), any(Duration.class))).thenReturn(Mono.just(true));
        stubActiveCount("005930_1_presence", 3L);

        StepVerifier.create(registry.enter(key, "m1"))
                .expectNext(3L)
                .verifyComplete();

        verify(zset).add(eq("005930_1_presence"), eq("m1"), any(Double.class));
    }

    @Test
    void heartbeat_refreshesMemberScore() {
        RoomKey key = new RoomKey("005930", 1);
        when(zset.add(eq("005930_1_presence"), eq("m1"), any(Double.class))).thenReturn(Mono.just(false));
        when(redis.expire(eq("005930_1_presence"), any(Duration.class))).thenReturn(Mono.just(true));
        stubActiveCount("005930_1_presence", 5L);

        StepVerifier.create(registry.heartbeat(key, "m1"))
                .expectNext(5L)
                .verifyComplete();

        verify(zset).add(eq("005930_1_presence"), eq("m1"), any(Double.class));
    }

    @Test
    void leave_removesMemberAndReturnsActiveCount() {
        RoomKey key = new RoomKey("005930", 1);
        when(zset.remove("005930_1_presence", "m1")).thenReturn(Mono.just(1L));
        stubActiveCount("005930_1_presence", 0L);

        StepVerifier.create(registry.leave(key, "m1"))
                .expectNext(0L)
                .verifyComplete();

        verify(zset).remove("005930_1_presence", "m1");
    }

    @Test
    void activeCount_prunesExpiredBeforeCounting() {
        // 만료(오래된 score) 멤버를 removeRangeByScore로 정리한 뒤 ZCARD로 센다.
        RoomKey key = new RoomKey("005930", 1);
        when(zset.remove("005930_1_presence", "m1")).thenReturn(Mono.just(0L));
        stubActiveCount("005930_1_presence", 4L);

        StepVerifier.create(registry.leave(key, "m1"))
                .expectNext(4L)
                .verifyComplete();

        verify(zset).removeRangeByScore(eq("005930_1_presence"), any(Range.class));
        verify(zset).size("005930_1_presence");
    }

    /** activeCount(정리 후 ZCARD) 경로 스텁: removeRangeByScore → size. */
    private void stubActiveCount(String presenceKey, long size) {
        when(zset.removeRangeByScore(eq(presenceKey), any(Range.class))).thenReturn(Mono.just(0L));
        when(zset.size(presenceKey)).thenReturn(Mono.just(size));
    }
}
