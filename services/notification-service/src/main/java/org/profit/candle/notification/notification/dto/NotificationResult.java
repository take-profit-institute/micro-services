package org.profit.candle.notification.notification.dto;

import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.profit.candle.notification.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResult(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String body,
        NotificationStatus status,
        String metaJson,
        Instant triggeredAt,
        Instant readAt,
        Instant createdAt
) {
}
