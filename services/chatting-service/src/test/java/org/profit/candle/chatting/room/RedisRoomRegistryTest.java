package org.profit.candle.chatting.room;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRoomRegistryTest {

    ReactiveStringRedisTemplate redis;
    ReactiveValueOperations<String, String> values;
    RedisRoomRegistry registry;

    @BeforeEach
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        registry = new RedisRoomRegistry(redis, new ChatProperties(
                new ChatProperties.Jwt("http://unused/.well-known/jwks.json", "candle-auth-test", "candle-api"),
                new ChatProperties.Room(2, Duration.ofHours(2)),
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
    void assign_existingRoomUnderCapacity_returnsExistingRoom() {
        when(values.get("chat:rooms:005930")).thenReturn(Mono.just("2"));
        when(values.get("005930_1_count")).thenReturn(Mono.just("2"));
        when(values.get("005930_2_count")).thenReturn(Mono.just("1"));

        StepVerifier.create(registry.assign("005930"))
                .expectNext(new RoomAssignment("005930", 2, "005930_2", "chat:005930_2", 1L))
                .verifyComplete();
    }

    @Test
    void assign_allRoomsFull_allocatesNewRoom() {
        when(values.get("chat:rooms:005930")).thenReturn(Mono.just("2"));
        when(values.get("005930_1_count")).thenReturn(Mono.just("2"));
        when(values.get("005930_2_count")).thenReturn(Mono.just("2"));
        when(values.increment("chat:rooms:005930")).thenReturn(Mono.just(3L));

        StepVerifier.create(registry.assign("005930"))
                .expectNext(new RoomAssignment("005930", 3, "005930_3", "chat:005930_3", 0L))
                .verifyComplete();
    }

    @Test
    void enter_incrementsCountAndRefreshesTtl() {
        RoomKey key = new RoomKey("005930", 1);
        when(values.increment("005930_1_count")).thenReturn(Mono.just(3L));
        when(redis.expire("005930_1_count", Duration.ofHours(2))).thenReturn(Mono.just(true));

        StepVerifier.create(registry.enter(key))
                .expectNext(3L)
                .verifyComplete();

        verify(redis).expire("005930_1_count", Duration.ofHours(2));
    }

    @Test
    void leave_decrementsCount() {
        RoomKey key = new RoomKey("005930", 1);
        when(values.decrement("005930_1_count")).thenReturn(Mono.just(1L));

        StepVerifier.create(registry.leave(key))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void leave_belowZero_floorsToZero() {
        // enter/leave 비대칭이나 TTL 만료로 카운터가 음수로 내려가면 0으로 바닥을 친다
        RoomKey key = new RoomKey("005930", 1);
        when(values.decrement("005930_1_count")).thenReturn(Mono.just(-1L));
        when(values.set("005930_1_count", "0")).thenReturn(Mono.just(true));

        StepVerifier.create(registry.leave(key))
                .expectNext(0L)
                .verifyComplete();

        verify(values).set("005930_1_count", "0");
    }
}
