package org.profit.candle.learning.event;

import java.time.Instant;
import java.util.UUID;

public record LearningCompletedEvent(
        UUID eventId,
        String eventType,
        UUID userId,
        UUID contentId,
        Instant occurredAt,
        int schemaVersion
) {
    public static LearningCompletedEvent create(UUID userId, UUID contentId, Instant occurredAt) {
        return new LearningCompletedEvent(
                UUID.randomUUID(),
                LearningEventType.LEARNING_COMPLETED.wireName(),
                userId, contentId, occurredAt,
                LearningEventType.LEARNING_COMPLETED.version()
        );
    }
}