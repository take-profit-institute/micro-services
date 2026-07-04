package org.profit.candle.notification.notification.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.profit.candle.notification.delivery.repository.NotificationDeliveryReader;
import org.profit.candle.notification.delivery.service.NotificationDeliveryService;
import org.profit.candle.notification.device.repository.DeviceTokenReader;
import org.profit.candle.notification.notification.dto.CreateNotificationCommand;
import org.profit.candle.notification.notification.dto.DeleteNotificationResult;
import org.profit.candle.notification.notification.dto.DeliveryResult;
import org.profit.candle.notification.notification.dto.ListNotificationsCriteria;
import org.profit.candle.notification.notification.dto.ListNotificationsResult;
import org.profit.candle.notification.notification.dto.MarkAllAsReadResult;
import org.profit.candle.notification.notification.dto.NotificationResult;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.profit.candle.notification.notification.repository.NotificationReader;
import org.profit.candle.notification.notification.repository.NotificationWriter;
import org.profit.candle.notification.outbox.service.OutboxEventService;
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
    private final OutboxEventService outboxEventService;

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
                saveNotificationWithOutbox(notification, command.idempotencyKey())
        );
        deviceTokenReader.listActiveByUserId(saved.getUserId())
                .forEach(deviceToken -> notificationDeliveryService.deliver(saved, deviceToken));

        return toResult(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ListNotificationsResult list(UUID userId, int pageSize, String pageToken) {
        int normalizedPageSize = normalizePageSize(pageSize);
        PageCursor cursor = decodePageToken(pageToken);
        List<Notification> notifications = notificationReader.listByCriteria(
                new ListNotificationsCriteria(
                        userId,
                        normalizedPageSize + 1,
                        cursor.createdAt(),
                        cursor.id()
                )
        );

        boolean hasNext = notifications.size() > normalizedPageSize;
        List<Notification> page = hasNext
                ? notifications.subList(0, normalizedPageSize)
                : notifications;
        String nextPageToken = hasNext ? encodePageToken(page.getLast()) : "";

        List<NotificationResult> results = page.stream()
                .map(this::toResult)
                .toList();

        return new ListNotificationsResult(results, nextPageToken);
    }

    @Override
    @Transactional
    public NotificationResult markAsRead(UUID userId, UUID notificationId, String idempotencyKey) {
        Notification notification = notificationReader.findById(notificationId)
                .orElseThrow(() -> new NotificationException(
                        NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markAsRead();
        outboxEventService.recordNotificationRead(notification, idempotencyKey);

        return toResult(notification);
    }

    @Override
    @Transactional
    public MarkAllAsReadResult markAllAsRead(UUID userId, String idempotencyKey) {
        List<Notification> unread =
                notificationReader.listByUserIdAndStatus(userId, NotificationStatus.UNREAD);
        for (Notification notification : unread) {
            notification.markAsRead();
            outboxEventService.recordNotificationRead(notification, idempotencyKey);
        }
        return new MarkAllAsReadResult(unread.size());
    }

    @Override
    @Transactional
    public DeleteNotificationResult deleteNotification(
            UUID userId,
            UUID notificationId,
            String idempotencyKey
    ) {
        // 이미 삭제된 경우는 멱등 성공(false)으로 처리 — 존재하지 않으면 no-op.
        Notification notification = notificationReader.findById(notificationId).orElse(null);
        if (notification == null) {
            return new DeleteNotificationResult(false);
        }
        if (!notification.getUserId().equals(userId)) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
        notificationWriter.delete(notification);
        return new DeleteNotificationResult(true);
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

    private Notification saveNotificationWithOutbox(
            Notification notification,
            String idempotencyKey
    ) {
        Notification saved = notificationWriter.save(notification);
        outboxEventService.recordNotificationCreated(saved, idempotencyKey);
        return saved;
    }

    private PageCursor decodePageToken(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return new PageCursor(null, null);
        }

        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(pageToken),
                    StandardCharsets.UTF_8
            );
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new PageCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new NotificationException(NotificationErrorCode.INVALID_REQUEST, e);
        }
    }

    private String encodePageToken(Notification notification) {
        String rawToken = notification.getCreatedAt() + "|" + notification.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawToken.getBytes(StandardCharsets.UTF_8));
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

    private record PageCursor(
            Instant createdAt,
            UUID id
    ) {
    }
}
