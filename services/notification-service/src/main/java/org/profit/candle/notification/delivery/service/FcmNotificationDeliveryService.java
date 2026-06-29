package org.profit.candle.notification.delivery.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.profit.candle.notification.delivery.repository.NotificationDeliveryWriter;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.notification.entity.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmNotificationDeliveryService implements NotificationDeliveryService {

    private final FirebaseMessaging firebaseMessaging;
    private final NotificationDeliveryWriter notificationDeliveryWriter;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Notification notification, DeviceToken deviceToken) {
        NotificationDelivery delivery = notificationDeliveryWriter.save(
                NotificationDelivery.pending(notification.getId(), deviceToken.getId())
        );

        try {
            String fcmMessageId = firebaseMessaging.send(toMessage(notification, deviceToken));
            delivery.sent(fcmMessageId);
        } catch (FirebaseMessagingException e) {
            delivery.failed(e.getMessage());
        } catch (RuntimeException e) {
            delivery.failed(e.getMessage());
        }

        notificationDeliveryWriter.save(delivery);
    }

    private Message toMessage(Notification notification, DeviceToken deviceToken) {
        Message.Builder builder = Message.builder()
                .setToken(deviceToken.getFcmToken())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getBody())
                        .build());

        if (notification.getMeta() != null && !notification.getMeta().isBlank()) {
            builder.putData("meta_json", notification.getMeta());
        }

        return builder.build();
    }
}
