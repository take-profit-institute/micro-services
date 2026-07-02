package org.profit.candle.wishlist.notification.client;

import java.util.UUID;

public interface NotificationClient {
    UUID createPriceAlertNotification(PriceAlertNotificationCommand command);
}
