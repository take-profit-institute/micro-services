package org.profit.candle.notification.delivery.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.profit.candle.notification.delivery.repository.NotificationDeliveryWriter;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.fcm.client.FcmClient;
import org.profit.candle.notification.notification.entity.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmNotificationDeliveryService implements NotificationDeliveryService {

    private final FcmClient fcmClient;
    private final NotificationDeliveryWriter notificationDeliveryWriter;

    @Override
    @Transactional
    public void deliver(Notification notification, DeviceToken deviceToken) {
        NotificationDelivery delivery = notificationDeliveryWriter.save(
                NotificationDelivery.pending(notification.getId(), deviceToken.getId())
        );

        try {
            String fcmMessageId = fcmClient.send(
                    deviceToken.getFcmToken(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getMeta()
            );
            delivery.sent(fcmMessageId);
        } catch (RuntimeException e) {
            delivery.failed(e.getMessage());
        }

        notificationDeliveryWriter.save(delivery);
    }
}
