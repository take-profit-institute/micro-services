package org.profit.candle.notification.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.notification.dto.ListNotificationsCriteria;
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
    public List<Notification> listByCriteria(ListNotificationsCriteria criteria) {
        PageRequest pageRequest = PageRequest.of(0, criteria.pageSize());
        if (criteria.cursorCreatedAt() == null || criteria.cursorId() == null) {
            return notificationJpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                    criteria.userId(),
                    pageRequest
            );
        }

        return notificationJpaRepository.findByUserIdAfterCursor(
                criteria.userId(),
                criteria.cursorCreatedAt(),
                criteria.cursorId(),
                pageRequest
        );
    }

    @Override
    public Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId) {
        return notificationJpaRepository.findByIdAndUserId(notificationId, userId);
    }

    @Override
    public Optional<Notification> findById(UUID notificationId) {
        return notificationJpaRepository.findById(notificationId);
    }

    @Override
    public long countByUserIdAndStatus(UUID userId, NotificationStatus status) {
        return notificationJpaRepository.countByUserIdAndStatus(userId, status);
    }
}
