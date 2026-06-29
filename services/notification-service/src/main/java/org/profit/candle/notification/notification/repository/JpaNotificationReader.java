package org.profit.candle.notification.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaNotificationReader implements NotificationReader {

    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    public List<Notification> listByUserId(UUID userId, int pageSize) {
        return notificationJpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                userId,
                PageRequest.of(0, pageSize)
        );
    }

    @Override
    public Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId) {
        return notificationJpaRepository.findByIdAndUserId(notificationId, userId);
    }

    @Override
    public long countByUserIdAndStatus(UUID userId, NotificationStatus status) {
        return notificationJpaRepository.countByUserIdAndStatus(userId, status);
    }
}
