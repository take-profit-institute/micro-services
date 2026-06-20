package org.profit.candle.common.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        String aggregateId,
        Instant occurredAt,
        T payload
) {
    public static <T> DomainEvent<T> create(String eventType, String aggregateId, T payload) {
        return new DomainEvent<>(UUID.randomUUID(), eventType, aggregateId, Instant.now(), payload);
    }
}
