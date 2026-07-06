package org.profit.candle.learning.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.learning.exception.LearningException;
import org.profit.candle.learning.idempotency.entity.IdempotencyRecord;
import org.profit.candle.learning.idempotency.repository.IdempotencyRecordRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyExecutorTest {

    @InjectMocks
    private IdempotencyExecutor sut;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("idempotency_key null이면 예외")
    void nullKey() {
        assertThatThrownBy(() ->
                sut.execute(userId, "op", null, "hash", String.class, () -> "result"))
                .isInstanceOf(LearningException.class);
    }

    @Test
    @DisplayName("idempotency_key 빈 문자열이면 예외")
    void blankKey() {
        assertThatThrownBy(() ->
                sut.execute(userId, "op", "  ", "hash", String.class, () -> "result"))
                .isInstanceOf(LearningException.class);
    }

    @Test
    @DisplayName("첫 요청 — action 실행 + 레코드 저장")
    void firstRequest() {
        given(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(
                userId, "CreateContent", "key-1"))
                .willReturn(Optional.empty());
        given(idempotencyRecordRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = sut.execute(userId, "CreateContent", "key-1",
                "hash-abc", String.class, () -> "created");

        assertThat(result).isEqualTo("created");
        then(idempotencyRecordRepository).should().save(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("동일 key + 동일 hash 재요청 — 저장된 결과 반환, action 미실행")
    void duplicateRequest() {
        IdempotencyRecord existing = IdempotencyRecord.create(
                userId, "CreateContent", "key-1", "hash-abc",
                "\"cached\"", String.class.getName());
        given(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(
                userId, "CreateContent", "key-1"))
                .willReturn(Optional.of(existing));

        String result = sut.execute(userId, "CreateContent", "key-1",
                "hash-abc", String.class, () -> { throw new RuntimeException("should not run"); });

        assertThat(result).isEqualTo("cached");
    }

    @Test
    @DisplayName("동일 key + 다른 hash — IDEMPOTENCY_REQUEST_MISMATCH 예외")
    void mismatchRequest() {
        IdempotencyRecord existing = IdempotencyRecord.create(
                userId, "CreateContent", "key-1", "hash-abc",
                "\"cached\"", String.class.getName());
        given(idempotencyRecordRepository.findByUserIdAndOperationAndIdempotencyKey(
                userId, "CreateContent", "key-1"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                sut.execute(userId, "CreateContent", "key-1",
                        "hash-DIFFERENT", String.class, () -> "result"))
                .isInstanceOf(LearningException.class);
    }
}