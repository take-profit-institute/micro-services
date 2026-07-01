package org.profit.candle.notification.notification.repository;

import org.profit.candle.notification.notification.entity.Notification;

public interface NotificationWriter {

    Notification save(Notification notification);
}
