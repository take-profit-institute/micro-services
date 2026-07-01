package org.profit.candle.notification.notification.dto;

import java.time.Instant;
import java.util.UUID;

import org.profit.candle.notification.delivery.entity.DeliveryStatus;

public record DeliveryResult(
        UUID id,
        UUID notificationId,
        UUID deviceTokenId,
        DeliveryStatus status,
        String fcmMessageId,
        String errorMessage,
        Instant sentAt,
        Instant createdAt
) {
}
