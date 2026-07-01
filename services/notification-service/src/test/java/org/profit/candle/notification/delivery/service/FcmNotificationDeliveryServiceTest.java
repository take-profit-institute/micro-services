package org.profit.candle.notification.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.profit.candle.notification.delivery.entity.DeliveryStatus;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.profit.candle.notification.delivery.repository.NotificationDeliveryWriter;
import org.profit.candle.notification.device.entity.DevicePlatform;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.fcm.client.FcmClient;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationType;

class FcmNotificationDeliveryServiceTest {

    @Test
    void shouldMarkDeliverySentWhenFcmClientSucceeds() {
        FakeNotificationDeliveryWriter writer = new FakeNotificationDeliveryWriter();
        FcmNotificationDeliveryService service = new FcmNotificationDeliveryService(
                (token, title, body, metaJson) -> "message-id",
                writer
        );

        service.deliver(notification(), deviceToken());

        NotificationDelivery delivery = writer.lastSaved();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getFcmMessageId()).isEqualTo("message-id");
        assertThat(delivery.getSentAt()).isNotNull();
    }

    @Test
    void shouldMarkDeliveryFailedWhenFcmClientFails() {
        FakeNotificationDeliveryWriter writer = new FakeNotificationDeliveryWriter();
        FcmClient fcmClient = (token, title, body, metaJson) -> {
            throw new RuntimeException("send failed");
        };
        FcmNotificationDeliveryService service = new FcmNotificationDeliveryService(
                fcmClient,
                writer
        );

        service.deliver(notification(), deviceToken());

        NotificationDelivery delivery = writer.lastSaved();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getErrorMessage()).isEqualTo("send failed");
        assertThat(delivery.getSentAt()).isNull();
    }

    private Notification notification() {
        return Notification.create(
                UUID.randomUUID(),
                NotificationType.PRICE_RISE,
                "title",
                "body",
                "{\"a\":1}"
        );
    }

    private DeviceToken deviceToken() {
        return DeviceToken.register(
                UUID.randomUUID(),
                "fcm-token",
                DevicePlatform.ANDROID,
                "device-id"
        );
    }

    private static final class FakeNotificationDeliveryWriter
            implements NotificationDeliveryWriter {

        private final List<NotificationDelivery> saved = new ArrayList<>();

        @Override
        public NotificationDelivery save(NotificationDelivery notificationDelivery) {
            saved.add(notificationDelivery);
            return notificationDelivery;
        }

        private NotificationDelivery lastSaved() {
            return saved.getLast();
        }
    }
}
