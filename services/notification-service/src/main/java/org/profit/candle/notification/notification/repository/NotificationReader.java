package org.profit.candle.notification.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.notification.notification.dto.ListNotificationsCriteria;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationStatus;

public interface NotificationReader {

    List<Notification> listByUserId(UUID userId, int pageSize);

    List<Notification> listByCriteria(ListNotificationsCriteria criteria);

    Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId);

    Optional<Notification> findById(UUID notificationId);

    List<Notification> listByUserIdAndStatus(UUID userId, NotificationStatus status);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);
}
