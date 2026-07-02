package org.profit.candle.notification.device.repository;

import org.profit.candle.notification.device.entity.DeviceToken;

public interface DeviceTokenWriter {

    DeviceToken save(DeviceToken deviceToken);
}
