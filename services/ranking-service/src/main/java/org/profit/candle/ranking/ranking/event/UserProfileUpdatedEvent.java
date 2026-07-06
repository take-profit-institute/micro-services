package org.profit.candle.ranking.ranking.event;

import java.time.Instant;
import java.util.UUID;

public record UserProfileUpdatedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        String userId,
        String nickname,
        String profileImageUrl,
        Instant occurredAt) {}
