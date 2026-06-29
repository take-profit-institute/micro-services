package org.profit.candle.notification.device.service;

import org.profit.candle.notification.device.dto.DeviceTokenResult;
import org.profit.candle.notification.device.dto.RegisterDeviceTokenCommand;

public interface DeviceTokenService {

    DeviceTokenResult register(RegisterDeviceTokenCommand command);
}