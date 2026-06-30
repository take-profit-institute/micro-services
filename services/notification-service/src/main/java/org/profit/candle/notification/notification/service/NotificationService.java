package org.profit.candle.notification.notification.service;

import org.profit.candle.notification.notification.dto.CreateNotificationCommand;
import org.profit.candle.notification.notification.dto.DeliveryResult;
import org.profit.candle.notification.notification.dto.ListNotificationsResult;
import org.profit.candle.notification.notification.dto.NotificationResult;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    NotificationResult createAndSend(CreateNotificationCommand command);

    ListNotificationsResult list(UUID userId, int pageSize, String pageToken);

    NotificationResult markAsRead(UUID userId, UUID notificationId, String idempotencyKey);

    long countUnread(UUID userId);

    List<DeliveryResult> getDeliveryStatus(UUID notificationId);
}
