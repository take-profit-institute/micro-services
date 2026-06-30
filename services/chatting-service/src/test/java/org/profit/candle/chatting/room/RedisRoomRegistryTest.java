package org.profit.candle.chatting.room;

import java.time.Duration;
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
                new ChatProperties.Jwt("12345678901234567890123456789012"),
                new ChatProperties.Room(2, Duration.ofHours(2))));
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
}
