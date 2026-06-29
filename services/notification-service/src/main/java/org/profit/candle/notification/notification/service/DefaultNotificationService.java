package org.profit.candle.notification.notification.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.profit.candle.notification.delivery.repository.NotificationDeliveryReader;
import org.profit.candle.notification.delivery.service.NotificationDeliveryService;
import org.profit.candle.notification.device.repository.DeviceTokenReader;
import org.profit.candle.notification.notification.dto.CreateNotificationCommand;
import org.profit.candle.notification.notification.dto.DeliveryResult;
import org.profit.candle.notification.notification.dto.ListNotificationsResult;
import org.profit.candle.notification.notification.dto.NotificationResult;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.profit.candle.notification.notification.repository.NotificationReader;
import org.profit.candle.notification.notification.repository.NotificationWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DefaultNotificationService implements NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationReader notificationReader;
    private final NotificationWriter notificationWriter;
    private final NotificationDeliveryReader notificationDeliveryReader;
    private final DeviceTokenReader deviceTokenReader;
    private final NotificationDeliveryService notificationDeliveryService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public NotificationResult createAndSend(CreateNotificationCommand command) {
        Notification notification = Notification.create(
                command.userId(),
                command.type(),
                command.title(),
                command.body(),
                command.metaJson()
        );

        Notification saved = transactionTemplate.execute(status ->
                notificationWriter.save(notification)
        );
        deviceTokenReader.listActiveByUserId(saved.getUserId())
                .forEach(deviceToken -> notificationDeliveryService.deliver(saved, deviceToken));

        return toResult(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ListNotificationsResult list(UUID userId, int pageSize, String pageToken) {
        int normalizedPageSize = normalizePageSize(pageSize);
        List<NotificationResult> notifications = notificationReader
                .listByUserId(userId, normalizedPageSize)
                .stream()
                .map(this::toResult)
                .toList();

        return new ListNotificationsResult(notifications, "");
    }

    @Override
    @Transactional
    public NotificationResult markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationReader.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationException(
                        NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();

        return toResult(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationReader.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryResult> getDeliveryStatus(UUID notificationId) {
        return notificationDeliveryReader.listByNotificationId(notificationId)
                .stream()
                .map(this::toResult)
                .toList();
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private NotificationResult toResult(Notification notification) {
        return new NotificationResult(
                notification.getId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getStatus(),
                notification.getMeta(),
                notification.getTriggeredAt(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private DeliveryResult toResult(NotificationDelivery delivery) {
        return new DeliveryResult(
                delivery.getId(),
                delivery.getNotificationId(),
                delivery.getDeviceTokenId(),
                delivery.getStatus(),
                delivery.getFcmMessageId(),
                delivery.getErrorMessage(),
                delivery.getSentAt(),
                delivery.getCreatedAt()
        );
    }
}
