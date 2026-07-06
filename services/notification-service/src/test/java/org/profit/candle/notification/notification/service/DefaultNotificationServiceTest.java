package org.profit.candle.notification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.notification.delivery.entity.NotificationDelivery;
import org.profit.candle.notification.delivery.repository.NotificationDeliveryReader;
import org.profit.candle.notification.delivery.service.NotificationDeliveryService;
import org.profit.candle.notification.device.entity.DevicePlatform;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.device.repository.DeviceTokenReader;
import org.profit.candle.notification.notification.dto.CreateNotificationCommand;
import org.profit.candle.notification.notification.dto.ListNotificationsCriteria;
import org.profit.candle.notification.notification.dto.ListNotificationsResult;
import org.profit.candle.notification.notification.dto.NotificationResult;
import org.profit.candle.notification.notification.entity.Notification;
import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.profit.candle.notification.notification.entity.NotificationType;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.profit.candle.notification.notification.repository.NotificationReader;
import org.profit.candle.notification.notification.repository.NotificationWriter;
import org.profit.candle.notification.outbox.entity.OutboxEvent;
import org.profit.candle.notification.outbox.repository.OutboxEventWriter;
import org.profit.candle.notification.outbox.service.OutboxEventService;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class DefaultNotificationServiceTest {

    private FakeNotificationRepository notificationRepository;
    private FakeDeviceTokenReader deviceTokenReader;
    private NotificationDeliveryReader deliveryReader;
    private NotificationDeliveryService deliveryService;
    private FakeOutboxEventWriter outboxEventWriter;
    private DefaultNotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = new FakeNotificationRepository();
        deviceTokenReader = new FakeDeviceTokenReader();
        deliveryReader = notificationId -> List.of();
        deliveryService = mock(NotificationDeliveryService.class);
        outboxEventWriter = new FakeOutboxEventWriter();
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        service = new DefaultNotificationService(
                notificationRepository,
                notificationRepository,
                deliveryReader,
                deviceTokenReader,
                deliveryService,
                transactionTemplate,
                new OutboxEventService(outboxEventWriter, new ObjectMapper())
        );
    }

    @Test
    void shouldSaveNotificationSendToActiveDeviceTokensAndRecordOutbox() {
        UUID userId = UUID.randomUUID();
        DeviceToken firstToken = DeviceToken.register(
                userId,
                "token-1",
                DevicePlatform.ANDROID,
                "device-1"
        );
        DeviceToken secondToken = DeviceToken.register(
                userId,
                "token-2",
                DevicePlatform.IOS,
                "device-2"
        );
        deviceTokenReader.deviceTokens.add(firstToken);
        deviceTokenReader.deviceTokens.add(secondToken);

        NotificationResult result = service.createAndSend(new CreateNotificationCommand(
                userId,
                NotificationType.BUY_FILLED,
                "filled",
                "order filled",
                "{\"order\":1}",
                "idem-create"
        ));

        assertThat(notificationRepository.notifications).hasSize(1);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(NotificationStatus.UNREAD);
        verify(deliveryService, times(1))
                .deliver(notificationRepository.notifications.getFirst(), firstToken);
        verify(deliveryService, times(1))
                .deliver(notificationRepository.notifications.getFirst(), secondToken);
        assertThat(outboxEventWriter.saved)
                .extracting(OutboxEvent::getEventType)
                .containsExactly("NotificationCreated");
    }

    @Test
    void shouldMarkOwnNotificationAsRead() {
        UUID userId = UUID.randomUUID();
        Notification notification = Notification.create(
                userId,
                NotificationType.PRICE_FALL,
                "title",
                "body",
                null
        );
        notificationRepository.notifications.add(notification);

        NotificationResult result = service.markAsRead(
                userId,
                notification.getId(),
                "idem-read"
        );

        assertThat(result.status()).isEqualTo(NotificationStatus.READ);
        assertThat(result.readAt()).isNotNull();
        assertThat(outboxEventWriter.saved)
                .extracting(OutboxEvent::getEventType)
                .containsExactly("NotificationRead");
    }

    @Test
    void shouldThrowNotFoundWhenNotificationDoesNotExist() {
        assertThatThrownBy(() -> service.markAsRead(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "idem-read"
        ))
                .isInstanceOf(NotificationException.class)
                .extracting(error -> ((NotificationException) error).errorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void shouldThrowAccessDeniedWhenNotificationBelongsToOtherUser() {
        Notification notification = Notification.create(
                UUID.randomUUID(),
                NotificationType.PRICE_RISE,
                "title",
                "body",
                null
        );
        notificationRepository.notifications.add(notification);

        assertThatThrownBy(() -> service.markAsRead(
                UUID.randomUUID(),
                notification.getId(),
                "idem-read"
        ))
                .isInstanceOf(NotificationException.class)
                .extracting(error -> ((NotificationException) error).errorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
    }

    @Test
    void shouldListNotificationsByCreatedAtDescAndIdDescWithPageSizeLimit() {
        UUID userId = UUID.randomUUID();
        Notification oldest = notification(userId, "2026-06-29T00:00:00Z", uuidEndingWith("1"));
        Notification sameTimeLowerId = notification(
                userId,
                "2026-06-30T00:00:00Z",
                uuidEndingWith("2")
        );
        Notification sameTimeHigherId = notification(
                userId,
                "2026-06-30T00:00:00Z",
                uuidEndingWith("3")
        );
        notificationRepository.notifications.add(oldest);
        notificationRepository.notifications.add(sameTimeLowerId);
        notificationRepository.notifications.add(sameTimeHigherId);

        ListNotificationsResult result = service.list(userId, 1, null);

        assertThat(result.notifications())
                .extracting(NotificationResult::id)
                .containsExactly(sameTimeHigherId.getId());
        assertThat(result.nextPageToken()).isNotBlank();
    }

    @Test
    void shouldRejectInvalidPageToken() {
        assertThatThrownBy(() -> service.list(UUID.randomUUID(), 20, "not-base64"))
                .isInstanceOf(NotificationException.class)
                .extracting(error -> ((NotificationException) error).errorCode())
                .isEqualTo(NotificationErrorCode.INVALID_REQUEST);
    }

    private Notification notification(UUID userId, String createdAt, UUID id) {
        Notification notification = Notification.create(
                userId,
                NotificationType.PRICE_RISE,
                "title",
                "body",
                null
        );
        setField(notification, "id", id);
        setField(notification, "createdAt", Instant.parse(createdAt));
        return notification;
    }

    private UUID uuidEndingWith(String suffix) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + suffix);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class FakeNotificationRepository
            implements NotificationReader, NotificationWriter {

        private final List<Notification> notifications = new ArrayList<>();

        @Override
        public List<Notification> listByUserId(UUID userId, int pageSize) {
            return sorted(userId).stream()
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public List<Notification> listByCriteria(ListNotificationsCriteria criteria) {
            return sorted(criteria.userId()).stream()
                    .filter(notification -> afterCursor(notification, criteria))
                    .limit(criteria.pageSize())
                    .toList();
        }

        @Override
        public Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId) {
            return notifications.stream()
                    .filter(notification -> notification.getId().equals(notificationId))
                    .filter(notification -> notification.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public Optional<Notification> findById(UUID notificationId) {
            return notifications.stream()
                    .filter(notification -> notification.getId().equals(notificationId))
                    .findFirst();
        }

        @Override
        public long countByUserIdAndStatus(UUID userId, NotificationStatus status) {
            return notifications.stream()
                    .filter(notification -> notification.getUserId().equals(userId))
                    .filter(notification -> notification.getStatus() == status)
                    .count();
        }

        @Override
        public List<Notification> listByUserIdAndStatus(UUID userId, NotificationStatus status) {
            return notifications.stream()
                    .filter(notification -> notification.getUserId().equals(userId))
                    .filter(notification -> notification.getStatus() == status)
                    .toList();
        }

        @Override
        public Notification save(Notification notification) {
            notifications.add(notification);
            return notification;
        }

        @Override
        public void delete(Notification notification) {
            notifications.removeIf(existing -> existing.getId().equals(notification.getId()));
        }

        private List<Notification> sorted(UUID userId) {
            return notifications.stream()
                    .filter(notification -> notification.getUserId().equals(userId))
                    .sorted(Comparator
                            .comparing(Notification::getCreatedAt)
                            .thenComparing(Notification::getId)
                            .reversed())
                    .toList();
        }

        private boolean afterCursor(Notification notification, ListNotificationsCriteria criteria) {
            if (criteria.cursorCreatedAt() == null || criteria.cursorId() == null) {
                return true;
            }
            if (notification.getCreatedAt().isBefore(criteria.cursorCreatedAt())) {
                return true;
            }
            return notification.getCreatedAt().equals(criteria.cursorCreatedAt())
                    && notification.getId().compareTo(criteria.cursorId()) < 0;
        }
    }

    private static final class FakeDeviceTokenReader implements DeviceTokenReader {

        private final List<DeviceToken> deviceTokens = new ArrayList<>();

        @Override
        public Optional<DeviceToken> findByFcmToken(String fcmToken) {
            return deviceTokens.stream()
                    .filter(deviceToken -> deviceToken.getFcmToken().equals(fcmToken))
                    .findFirst();
        }

        @Override
        public List<DeviceToken> listActiveByUserId(UUID userId) {
            return deviceTokens.stream()
                    .filter(deviceToken -> deviceToken.getUserId().equals(userId))
                    .filter(DeviceToken::isActive)
                    .toList();
        }
    }

    private static final class FakeOutboxEventWriter implements OutboxEventWriter {

        private final List<OutboxEvent> saved = new ArrayList<>();

        @Override
        public OutboxEvent save(OutboxEvent event) {
            saved.add(event);
            return event;
        }
    }
}
