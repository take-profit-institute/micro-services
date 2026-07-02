package org.profit.candle.notification.fcm.client;

import org.profit.candle.notification.notification.exception.NotificationException;

public interface FcmClient {

    /**
     * @throws NotificationException when FCM send fails
     */
    String send(String token, String title, String body, String metaJson);
}
