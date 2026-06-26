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

    static final String EVENT_TYPE = "UserProfileUpdated";
    static final String TOPIC = "user.profile-updated.v1";
    static final int VERSION = 1;

    static UserProfileUpdatedEvent of(String userId, String nickname, String profileImageUrl) {
        return new UserProfileUpdatedEvent(
                UUID.randomUUID(), EVENT_TYPE, VERSION,
                userId, nickname, profileImageUrl,
                Instant.now());
    }
}
