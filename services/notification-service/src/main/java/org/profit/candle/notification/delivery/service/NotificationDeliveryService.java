package org.profit.candle.notification.delivery.service;

import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.notification.entity.Notification;

public interface NotificationDeliveryService {

    void deliver(Notification notification, DeviceToken deviceToken);
}
