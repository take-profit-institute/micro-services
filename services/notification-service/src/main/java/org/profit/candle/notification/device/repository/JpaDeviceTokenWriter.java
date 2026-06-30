package org.profit.candle.notification.device.repository;

import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeviceTokenWriter implements DeviceTokenWriter {

    private final DeviceTokenJpaRepository deviceTokenJpaRepository;

    @Override
    public DeviceToken save(DeviceToken deviceToken) {
        return deviceTokenJpaRepository.save(deviceToken);
    }
}
