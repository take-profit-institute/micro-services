package org.profit.candle.notification.delivery.repository;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaNotificationDeliveryReader implements NotificationDeliveryReader {

    private final NotificationDeliveryJpaRepository notificationDeliveryJpaRepository;

    @Override
    public List<NotificationDelivery> listByNotificationId(UUID notificationId) {
        return notificationDeliveryJpaRepository
                .findByNotificationIdOrderByCreatedAtAsc(notificationId);
    }
}
