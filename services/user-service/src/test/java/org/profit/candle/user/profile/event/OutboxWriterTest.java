package org.profit.candle.user.profile.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.event.entity.OutboxEvent;
import org.profit.candle.user.profile.event.repository.OutboxEventRepository;

import java.time.Instant;

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

    private UserProfileResult result(String userId) {
        return new UserProfileResult(userId, "a@b.com", "nick", "url", false, Instant.now(), Instant.now(), 0L);
    }

    @Test
    void writeUserProfileUpdated_savesOutboxEventWithCorrectTopic() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        outboxWriter.writeUserProfileUpdated(result("user-1"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo(UserProfileEvents.TOPIC);
    }

    @Test
    void writeUserProfileUpdated_usesUserIdAsPartitionKey() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        outboxWriter.writeUserProfileUpdated(result("user-42"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().partitionKey()).isEqualTo("user-42");
    }

    @Test
    void writeUserProfileUpdated_eventHasCorrectTypeAndVersion() throws Exception {
        ArgumentCaptor<UserProfileUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserProfileUpdatedEvent.class);
        when(objectMapper.writeValueAsString(eventCaptor.capture())).thenReturn("{}");

        outboxWriter.writeUserProfileUpdated(result("user-1"));

        UserProfileUpdatedEvent captured = eventCaptor.getValue();
        assertThat(captured.eventType()).isEqualTo(UserProfileEvents.EVENT_TYPE);
        assertThat(captured.eventVersion()).isEqualTo(UserProfileEvents.VERSION);
        assertThat(captured.userId()).isEqualTo("user-1");
        assertThat(captured.nickname()).isEqualTo("nick");
        assertThat(captured.profileImageUrl()).isEqualTo("url");
    }

    @Test
    void writeUserProfileUpdated_serializationFailure_throwsIllegalState() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> outboxWriter.writeUserProfileUpdated(result("user-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("직렬화 실패");
    }
}
