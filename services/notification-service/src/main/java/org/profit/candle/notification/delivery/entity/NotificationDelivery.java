package org.profit.candle.notification.delivery.entity;

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
@Table(name = "notification_deliveries", schema = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class NotificationDelivery {

    @Id
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "device_token_id", nullable = false)
    private UUID deviceTokenId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notification.delivery_status")
    private DeliveryStatus status;

    @Column(name = "fcm_message_id")
    private String fcmMessageId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static NotificationDelivery pending(UUID notificationId, UUID deviceTokenId) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.id = UUID.randomUUID();
        delivery.notificationId = notificationId;
        delivery.deviceTokenId = deviceTokenId;
        delivery.status = DeliveryStatus.PENDING;
        delivery.createdAt = Instant.now();
        return delivery;
    }

    public void sent(String fcmMessageId) {
        this.status = DeliveryStatus.SENT;
        this.fcmMessageId = fcmMessageId;
        this.sentAt = Instant.now();
    }

    public void failed(String errorMessage) {
        this.status = DeliveryStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
