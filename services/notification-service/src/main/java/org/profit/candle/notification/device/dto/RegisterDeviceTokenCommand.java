package org.profit.candle.notification.device.dto;

import org.profit.candle.notification.device.entity.DevicePlatform;

import java.util.UUID;

public record RegisterDeviceTokenCommand(
        UUID userId,
        String fcmToken,
        DevicePlatform platform,
        String deviceId
) {
}