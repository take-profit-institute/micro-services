package org.profit.candle.notification.device.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenJpaRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    List<DeviceToken> findByUserIdAndActiveTrue(UUID userId);
}
