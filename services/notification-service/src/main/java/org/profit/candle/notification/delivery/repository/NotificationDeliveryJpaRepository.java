package org.profit.candle.notification.delivery.repository;

import java.util.List;
import java.util.UUID;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryJpaRepository
        extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByNotificationIdOrderByCreatedAtAsc(UUID notificationId);
}
