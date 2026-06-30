package org.profit.candle.notification.notification.dto;

import java.util.UUID;
import org.profit.candle.notification.notification.entity.NotificationType;

public record CreateNotificationCommand(
        UUID userId,
        NotificationType type,
        String title,
        String body,
        String metaJson,
        String idempotencyKey
) {
}
