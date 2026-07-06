package org.profit.candle.learning.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.event.entity.OutboxEvent;
import org.profit.candle.learning.event.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void recordLearningCompleted(UUID userId, UUID contentId) {
        Instant now = Instant.now();
        LearningCompletedEvent event = LearningCompletedEvent.create(userId, contentId, now);
        try {
            outboxEventRepository.save(new OutboxEvent(
                    event.eventId(),
                    event.eventType(),
                    userId.toString(),
                    objectMapper.writeValueAsString(event),
                    now));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("LearningCompleted event serialization failed", e);
        }
    }
}