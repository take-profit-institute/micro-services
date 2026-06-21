package org.profit.candle.auth.event;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(UUID eventId, String eventType, int eventVersion, UUID userId, String email, Instant occurredAt) {
    public static UserCreatedEvent create(UUID userId, String email, Instant occurredAt) {
        return new UserCreatedEvent(UUID.randomUUID(), AuthEventType.USER_CREATED.wireName(),
                AuthEventType.USER_CREATED.version(), userId, email, occurredAt);
    }
}
