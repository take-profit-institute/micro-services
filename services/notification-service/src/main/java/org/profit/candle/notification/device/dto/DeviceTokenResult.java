package org.profit.candle.notification.device.dto;

import org.profit.candle.notification.device.entity.DevicePlatform;

import java.time.Instant;
import java.util.UUID;

public record DeviceTokenResult(
        UUID id,
        UUID userId,
        String fcmToken,
        DevicePlatform platform,
        String deviceId,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}