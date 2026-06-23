package org.profit.candle.user.profile.event.dto;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedPayload(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID userId,
        String email,
        Instant occurredAt) {}
