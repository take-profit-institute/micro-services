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
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileEventPublisherTest {

    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @Mock ObjectMapper objectMapper;
    @InjectMocks UserProfileEventPublisher publisher;

    private UserProfileResult result(String userId) {
        return new UserProfileResult(userId, "a@b.com", "nick", "url", false, Instant.now(), Instant.now(), 0L);
    }

    @Test
    void publishUserProfileUpdated_sendsToCorrectTopic() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        publisher.publishUserProfileUpdated(result("user-1"));

        verify(kafkaTemplate).send(eq("user.profile-updated.v1"), eq("user-1"), anyString());
    }

    @Test
    void publishUserProfileUpdated_usesUserIdAsKey() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        publisher.publishUserProfileUpdated(result("user-42"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("user-42");
    }

    @Test
    void publishUserProfileUpdated_eventHasCorrectType() throws Exception {
        ArgumentCaptor<UserProfileUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserProfileUpdatedEvent.class);
        when(objectMapper.writeValueAsString(eventCaptor.capture())).thenReturn("{}");

        publisher.publishUserProfileUpdated(result("user-1"));

        UserProfileUpdatedEvent captured = eventCaptor.getValue();
        assertThat(captured.eventType()).isEqualTo("UserProfileUpdated");
        assertThat(captured.eventVersion()).isEqualTo(1);
        assertThat(captured.userId()).isEqualTo("user-1");
        assertThat(captured.nickname()).isEqualTo("nick");
        assertThat(captured.profileImageUrl()).isEqualTo("url");
    }

    @Test
    void publishUserProfileUpdated_serializationFailure_throwsIllegalState() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> publisher.publishUserProfileUpdated(result("user-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("직렬화 실패");
    }
}
