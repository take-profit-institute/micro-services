package org.profit.candle.notification.idempotency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.notification.idempotency.entity.IdempotencyRecord;
import org.profit.candle.notification.idempotency.repository.IdempotencyRecordJpaRepository;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;

class IdempotencyExecutorTest {

    private IdempotencyRecordJpaRepository repository;
    private AtomicReference<IdempotencyRecord> storedRecord;
    private IdempotencyExecutor executor;

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRecordJpaRepository.class);
        storedRecord = new AtomicReference<>();
        when(repository.findByUserIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(storedRecord.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            storedRecord.set(record);
            return record;
        });
        executor = new IdempotencyExecutor(repository, new ObjectMapper());
    }

    @Test
    void shouldReuseStoredResponseWhenSameKeyAndSameRequestHash() {
        UUID userId = UUID.randomUUID();
        AtomicInteger actionCount = new AtomicInteger();

        SampleResponse first = executor.execute(
                userId,
                "CreateNotification",
                "same-key",
                "hash-1",
                SampleResponse.class,
                () -> {
                    actionCount.incrementAndGet();
                    return new SampleResponse("stored");
                }
        );
        SampleResponse second = executor.execute(
                userId,
                "CreateNotification",
                "same-key",
                "hash-1",
                SampleResponse.class,
                () -> {
                    actionCount.incrementAndGet();
                    return new SampleResponse("new");
                }
        );

        assertThat(first.value()).isEqualTo("stored");
        assertThat(second.value()).isEqualTo("stored");
        assertThat(actionCount).hasValue(1);
    }

    @Test
    void shouldRejectSameKeyWithDifferentRequestHash() {
        UUID userId = UUID.randomUUID();
        executor.execute(
                userId,
                "CreateNotification",
                "same-key",
                "hash-1",
                SampleResponse.class,
                () -> new SampleResponse("stored")
        );

        assertThatThrownBy(() -> executor.execute(
                userId,
                "CreateNotification",
                "same-key",
                "hash-2",
                SampleResponse.class,
                () -> new SampleResponse("new")
        ))
                .isInstanceOf(NotificationException.class)
                .extracting(error -> ((NotificationException) error).errorCode())
                .isEqualTo(NotificationErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
    }

    @Test
    void shouldRejectMissingKey() {
        assertThatThrownBy(() -> executor.execute(
                UUID.randomUUID(),
                "CreateNotification",
                "",
                "hash",
                SampleResponse.class,
                () -> new SampleResponse("new")
        ))
                .isInstanceOf(NotificationException.class)
                .extracting(error -> ((NotificationException) error).errorCode())
                .isEqualTo(NotificationErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    private record SampleResponse(String value) {
    }
}
