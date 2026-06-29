package org.profit.candle.notification.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notification.notification_type")
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notification.notification_status")
    private NotificationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_json", columnDefinition = "jsonb")
    private String meta;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Notification create(
            UUID userId,
            NotificationType type,
            String title,
            String body,
            String meta
    ) {
        Instant now = Instant.now();

        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.status = NotificationStatus.UNREAD;
        notification.meta = meta;
        notification.triggeredAt = now;
        notification.createdAt = now;

        return notification;
    }

    public void markAsRead() {
        if (this.status == NotificationStatus.READ) {
            return;
        }

        this.status = NotificationStatus.READ;
        this.readAt = Instant.now();
    }
}
