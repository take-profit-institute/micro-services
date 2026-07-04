package org.profit.candle.notification.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    @Query("""
            SELECT n
            FROM Notification n
            WHERE n.userId = :userId
              AND (
                n.createdAt < :cursorCreatedAt
                OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId)
              )
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> findByUserIdAfterCursor(
            UUID userId,
            Instant cursorCreatedAt,
            UUID cursorId,
            Pageable pageable
    );

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Optional<Notification> findById(UUID id);

    List<Notification> findByUserIdAndStatus(UUID userId, NotificationStatus status);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);
}
