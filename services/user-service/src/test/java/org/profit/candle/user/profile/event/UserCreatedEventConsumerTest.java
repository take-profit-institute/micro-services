package org.profit.candle.user.profile.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.profit.candle.user.profile.event.entity.ConsumedEvent;
import org.profit.candle.user.profile.event.repository.ConsumedEventRepository;
import org.profit.candle.user.profile.repository.UserProfileWriter;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCreatedEventConsumerTest {

    @Mock ConsumedEventRepository consumedEventRepository;
    @Mock UserProfileWriter userProfileWriter;

    UserCreatedEventConsumer consumer;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        consumer = new UserCreatedEventConsumer(consumedEventRepository, userProfileWriter, objectMapper);
    }

    private String payload(UUID eventId, UUID userId) throws Exception {
        var payload = new org.profit.candle.user.profile.event.dto.UserCreatedPayload(
                eventId, "UserCreated", 1, userId, "a@b.com", Instant.now());
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void onUserCreated_happyPath_savesProfileAndConsumedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        UserProfileEntity saved = new UserProfileEntity(userId.toString(), "a@b.com", null, null);
        when(userProfileWriter.save(any())).thenReturn(saved);

        consumer.onUserCreated(payload(eventId, userId));

        ArgumentCaptor<UserProfileEntity> profileCaptor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileWriter).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().userId()).isEqualTo(userId.toString());
        assertThat(profileCaptor.getValue().email()).isEqualTo("a@b.com");

        ArgumentCaptor<ConsumedEvent> eventCaptor = ArgumentCaptor.forClass(ConsumedEvent.class);
        verify(consumedEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isEqualTo(eventId);
    }

    @Test
    void onUserCreated_duplicateEvent_skipsProcessing() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onUserCreated(payload(eventId, UUID.randomUUID()));

        verify(userProfileWriter, never()).save(any());
        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    void onUserCreated_invalidJson_returnsWithoutSaving() {
        consumer.onUserCreated("{invalid-json");

        verify(userProfileWriter, never()).save(any());
        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    void onUserCreated_dataIntegrityViolation_savesConsumedEventOnly() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        when(userProfileWriter.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        consumer.onUserCreated(payload(eventId, userId));

        // profile writer threw but consumed event should still be saved
        ArgumentCaptor<ConsumedEvent> captor = ArgumentCaptor.forClass(ConsumedEvent.class);
        verify(consumedEventRepository).save(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
    }
}
