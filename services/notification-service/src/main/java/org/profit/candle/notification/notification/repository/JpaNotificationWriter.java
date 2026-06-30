package org.profit.candle.notification.notification.repository;

import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.notification.entity.Notification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaNotificationWriter implements NotificationWriter {

    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(notification);
    }
}
