package org.profit.candle.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.event.entity.OutboxEvent;
import org.profit.candle.auth.event.repository.OutboxEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock OutboxEventRepository outboxEventRepository;
    @Mock ObjectMapper objectMapper;
    @InjectMocks OutboxWriter outboxWriter;

    @Test
    void recordUserCreated_savesEventWithCorrectType() throws Exception {
        UUID userId = UUID.randomUUID();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        outboxWriter.recordUserCreated(userId, "user@example.com");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("UserCreated");
    }

    @Test
    void recordUserCreated_usesUserIdAsAggregateId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        outboxWriter.recordUserCreated(userId, "user@example.com");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().aggregateId()).isEqualTo(userId.toString());
    }

    @Test
    void recordUserCreated_savesSerializedPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        String payload = "{\"userId\":\"" + userId + "\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(payload);

        outboxWriter.recordUserCreated(userId, "user@example.com");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().payload()).isEqualTo(payload);
    }

    @Test
    void recordUserCreated_serializationFailure_throwsIllegalState() throws Exception {
        UUID userId = UUID.randomUUID();
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("fail") {});

        assertThatThrownBy(() -> outboxWriter.recordUserCreated(userId, "user@example.com"))
                .isInstanceOf(IllegalStateException.class);
    }
}
