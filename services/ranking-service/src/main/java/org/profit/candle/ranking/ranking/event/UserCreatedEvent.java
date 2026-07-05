package org.profit.candle.ranking.ranking.event;

import java.time.Instant;
import java.util.UUID;

/** auth-service가 발행하는 사용자 생성 이벤트(auth.user-created.v1) 페이로드. */
public record UserCreatedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID userId,
        String email,
        Instant occurredAt) {}
