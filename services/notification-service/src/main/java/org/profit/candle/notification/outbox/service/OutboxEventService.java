package org.profit.candle.notification.outbox.service;

import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.profit.candle.notification.outbox.entity.OutboxEvent;
import org.profit.candle.notification.outbox.repository.OutboxEventWriter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private static final int EVENT_VERSION = 1;

    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    public void recordNotificationCreated(Notification notification, String idempotencyKey) {
        outboxEventWriter.save(OutboxEvent.create(
                "Notification",
                notification.getId(),
                "NotificationCreated",
                EVENT_VERSION,
                toJson(Map.of(
                        "notification_id", notification.getId().toString(),
                        "user_id", notification.getUserId().toString(),
                        "type", notification.getType().name(),
                        "status", notification.getStatus().name()
                )),
                idempotencyKey,
                null
        ));
    }

    public void recordNotificationRead(Notification notification, String idempotencyKey) {
        outboxEventWriter.save(OutboxEvent.create(
                "Notification",
                notification.getId(),
                "NotificationRead",
                EVENT_VERSION,
                toJson(Map.of(
                        "notification_id", notification.getId().toString(),
                        "user_id", notification.getUserId().toString(),
                        "status", notification.getStatus().name(),
                        "read_at", notification.getReadAt().toString()
                )),
                idempotencyKey,
                null
        ));
    }

    public void recordDeviceTokenRegistered(DeviceToken deviceToken, String idempotencyKey) {
        outboxEventWriter.save(OutboxEvent.create(
                "DeviceToken",
                deviceToken.getId(),
                "DeviceTokenRegistered",
                EVENT_VERSION,
                toJson(Map.of(
                        "device_token_id", deviceToken.getId().toString(),
                        "user_id", deviceToken.getUserId().toString(),
                        "platform", deviceToken.getPlatform().name(),
                        "active", deviceToken.isActive()
                )),
                idempotencyKey,
                null
        ));
    }

    private String toJson(Map<String, Object> payload) {
        return objectMapper.writeValueAsString(payload);
    }
}
