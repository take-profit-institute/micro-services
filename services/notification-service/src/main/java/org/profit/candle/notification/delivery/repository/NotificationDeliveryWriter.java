package org.profit.candle.notification.delivery.repository;

import org.profit.candle.notification.delivery.entity.NotificationDelivery;

public interface NotificationDeliveryWriter {

    NotificationDelivery save(NotificationDelivery notificationDelivery);
}
