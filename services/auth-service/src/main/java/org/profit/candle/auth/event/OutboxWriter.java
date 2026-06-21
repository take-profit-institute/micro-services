package org.profit.candle.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.event.entity.OutboxEvent;
import org.profit.candle.auth.event.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxWriter {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void recordUserCreated(UUID userId, String email) {
        Instant now = Instant.now();
        UserCreatedEvent event = UserCreatedEvent.create(userId, email, now);
        try {
            outboxEventRepository.save(new OutboxEvent(event.eventId(), event.eventType(), userId.toString(),
                    objectMapper.writeValueAsString(event), now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("UserCreated event serialization failed", exception);
        }
    }
}
