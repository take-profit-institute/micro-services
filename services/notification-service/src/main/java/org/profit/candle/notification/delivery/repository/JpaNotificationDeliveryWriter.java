package org.profit.candle.notification.delivery.repository;

import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaNotificationDeliveryWriter implements NotificationDeliveryWriter {

    private final NotificationDeliveryJpaRepository notificationDeliveryJpaRepository;

    @Override
    public NotificationDelivery save(NotificationDelivery notificationDelivery) {
        return notificationDeliveryJpaRepository.save(notificationDelivery);
    }
}
