package org.profit.candle.notification.fcm.client;

public interface FcmClient {

    String send(String token, String title, String body, String metaJson);
}