package org.profit.candle.notification.device.entity;

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
@Table(name = "device_tokens", schema = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DeviceToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fcm_token", nullable = false, unique = true, columnDefinition = "text")
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notification.device_platform")
    private DevicePlatform platform;

    @Column(name = "device_id")
    private String deviceId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DeviceToken register(
            UUID userId,
            String fcmToken,
            DevicePlatform platform,
            String deviceId
    ) {
        Instant now = Instant.now();

        DeviceToken token = new DeviceToken();
        token.id = UUID.randomUUID();
        token.userId = userId;
        token.fcmToken = fcmToken;
        token.platform = platform;
        token.deviceId = deviceId;
        token.active = true;
        token.createdAt = now;
        token.updatedAt = now;

        return token;
    }

    public void reactivate(UUID userId, DevicePlatform platform, String deviceId) {
        this.userId = userId;
        this.platform = platform;
        this.deviceId = deviceId;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
