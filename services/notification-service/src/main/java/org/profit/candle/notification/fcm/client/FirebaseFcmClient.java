package org.profit.candle.notification.fcm.client;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FirebaseFcmClient implements FcmClient {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public String send(String token, String title, String body, String metaJson) {
        try {
            return firebaseMessaging.send(toMessage(token, title, body, metaJson));
        } catch (FirebaseMessagingException e) {
            throw new NotificationException(NotificationErrorCode.FCM_SEND_FAILED, e);
        }
    }

    private Message toMessage(String token, String title, String body, String metaJson) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (metaJson != null && !metaJson.isBlank()) {
            builder.putData("meta_json", metaJson);
        }

        return builder.build();
    }
}
