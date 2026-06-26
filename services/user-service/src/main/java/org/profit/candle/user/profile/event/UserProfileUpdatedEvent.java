package org.profit.candle.user.profile.event;

import java.time.Instant;
import java.util.UUID;

public record UserProfileUpdatedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        String userId,
        String nickname,
        String profileImageUrl,
        Instant occurredAt) {

    public static UserProfileUpdatedEvent of(String userId, String nickname, String profileImageUrl) {
        return new UserProfileUpdatedEvent(
                UUID.randomUUID(), UserProfileEvents.EVENT_TYPE, UserProfileEvents.VERSION,
                userId, nickname, profileImageUrl,
                Instant.now());
    }
}
