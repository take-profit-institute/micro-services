package org.profit.candle.notification.device.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeviceTokenReader implements DeviceTokenReader {

    private final DeviceTokenJpaRepository deviceTokenJpaRepository;

    @Override
    public Optional<DeviceToken> findByFcmToken(String fcmToken) {
        return deviceTokenJpaRepository.findByFcmToken(fcmToken);
    }

    @Override
    public List<DeviceToken> listActiveByUserId(UUID userId) {
        return deviceTokenJpaRepository.findByUserIdAndActiveTrue(userId);
    }
}
