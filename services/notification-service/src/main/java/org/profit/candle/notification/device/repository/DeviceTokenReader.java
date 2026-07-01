package org.profit.candle.notification.device.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.notification.device.entity.DeviceToken;

public interface DeviceTokenReader {

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    List<DeviceToken> listActiveByUserId(UUID userId);
}
