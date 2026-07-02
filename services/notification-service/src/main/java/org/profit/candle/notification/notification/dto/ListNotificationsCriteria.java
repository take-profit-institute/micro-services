package org.profit.candle.notification.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record ListNotificationsCriteria(
        UUID userId,
        int pageSize,
        Instant cursorCreatedAt,
        UUID cursorId
) {
}
