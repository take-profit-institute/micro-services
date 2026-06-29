package org.profit.candle.notification.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationStatus;

public interface NotificationReader {

    List<Notification> listByUserId(UUID userId, int pageSize);

    Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);
}
