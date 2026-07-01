package org.profit.candle.notification.delivery.repository;

import java.util.List;
import java.util.UUID;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;

public interface NotificationDeliveryReader {

    List<NotificationDelivery> listByNotificationId(UUID notificationId);
}
