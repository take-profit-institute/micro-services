package org.profit.candle.notification.device.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.device.dto.DeviceTokenResult;
import org.profit.candle.notification.device.dto.RegisterDeviceTokenCommand;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.device.repository.DeviceTokenReader;
import org.profit.candle.notification.device.repository.DeviceTokenWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultDeviceTokenService implements DeviceTokenService {

    private final DeviceTokenReader deviceTokenReader;
    private final DeviceTokenWriter deviceTokenWriter;

    @Override
    @Transactional
    public DeviceTokenResult register(RegisterDeviceTokenCommand command) {
        DeviceToken deviceToken = deviceTokenReader.findByFcmToken(command.fcmToken())
                .map(existing -> {
                    existing.reactivate(
                            command.userId(),
                            command.platform(),
                            command.deviceId()
                    );
                    return existing;
                })
                .orElseGet(() -> DeviceToken.register(
                        command.userId(),
                        command.fcmToken(),
                        command.platform(),
                        command.deviceId()
                ));

        DeviceToken saved = deviceTokenWriter.save(deviceToken);

        return toResult(saved);
    }

    private DeviceTokenResult toResult(DeviceToken deviceToken) {
        return new DeviceTokenResult(
                deviceToken.getId(),
                deviceToken.getUserId(),
                deviceToken.getFcmToken(),
                deviceToken.getPlatform(),
                deviceToken.getDeviceId(),
                deviceToken.isActive(),
                deviceToken.getCreatedAt(),
                deviceToken.getUpdatedAt()
        );
    }
}
