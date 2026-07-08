package org.profit.candle.trading.support.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OutboxWriter 단위 테스트 — 실제 ObjectMapper로 직렬화하고, OutboxOperations는 mock으로
 * 대체한다. record()가 실제로 어느 도메인 서비스 테스트에서도 mock 처리돼서 직접
 * 실행된 적이 없어 support.event 패키지 분기 커버리지가 0%였다.
 */
@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    private record FakeEvent(UUID id, String eventType, String aggregateId, String payload, Instant occurredAt) {}

    @Mock private OutboxOperations<FakeEvent> ops;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new OutboxWriter(objectMapper);
    }

    /** getValue() 호출 시 예외를 던져 Jackson이 직렬화 도중 JacksonException을 던지도록 유도. */
    static class Unserializable {
        public String getValue() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    @DisplayName("정상 payload는 JSON으로 직렬화해 ops.save()에 위임한다")
    void shouldSerializePayloadAndDelegateToSave() {
        String aggregateId = UUID.randomUUID().toString();
        when(ops.newEvent(any(), eq("OrderPlaced"), eq(aggregateId), anyString(), any()))
                .thenAnswer(inv -> new FakeEvent(inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), inv.getArgument(3), inv.getArgument(4)));

        writer.record(ops, "OrderPlaced", aggregateId, new PayloadStub("005930", 10));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops).newEvent(any(), eq("OrderPlaced"), eq(aggregateId), payloadCaptor.capture(), any());
        assertThat(payloadCaptor.getValue()).contains("\"symbol\":\"005930\"").contains("\"quantity\":10");
        verify(ops).save(any(FakeEvent.class));
    }

    @Test
    @DisplayName("동일 호출 내에서 새로 만든 event를 그대로 save에 넘긴다 (변형 없이)")
    void shouldPassNewlyCreatedEventDirectlyToSave() {
        FakeEvent created = new FakeEvent(UUID.randomUUID(), "OrderCancelled", "agg-1", "{}", Instant.now());
        when(ops.newEvent(any(), anyString(), anyString(), anyString(), any())).thenReturn(created);

        writer.record(ops, "OrderCancelled", "agg-1", new PayloadStub("005930", 5));

        verify(ops).save(created);
    }

    @Test
    @DisplayName("payload 직렬화가 실패하면 IllegalStateException으로 감싸 던지고 ops.save()는 호출되지 않는다")
    void shouldWrapSerializationFailureAsIllegalStateException() {
        assertThatThrownBy(() -> writer.record(ops, "SomeEvent", "agg-1", new Unserializable()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SomeEvent")
                .hasMessageContaining("serialization failed");

        verify(ops, never()).save(any());
        verify(ops, never()).newEvent(any(), any(), any(), any(), any());
    }

    private record PayloadStub(String symbol, int quantity) {}
}