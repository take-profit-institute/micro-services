package org.profit.candle.notification.notification.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.delivery.entity.DeliveryStatus;
import org.profit.candle.notification.device.dto.DeviceTokenResult;
import org.profit.candle.notification.device.dto.RegisterDeviceTokenCommand;
import org.profit.candle.notification.device.entity.DevicePlatform;
import org.profit.candle.notification.device.service.DeviceTokenService;
import org.profit.candle.notification.idempotency.service.IdempotencyExecutor;
import org.profit.candle.notification.notification.dto.CreateNotificationCommand;
import org.profit.candle.notification.notification.dto.DeleteNotificationResult;
import org.profit.candle.notification.notification.dto.DeliveryResult;
import org.profit.candle.notification.notification.dto.ListNotificationsResult;
import org.profit.candle.notification.notification.dto.MarkAllAsReadResult;
import org.profit.candle.notification.notification.dto.NotificationResult;
import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.profit.candle.notification.notification.entity.NotificationType;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.profit.candle.notification.notification.service.NotificationService;
import org.profit.candle.proto.common.v1.PageResponse;
import org.profit.candle.proto.notification.v1.CreateNotificationRequest;
import org.profit.candle.proto.notification.v1.CreateNotificationResponse;
import org.profit.candle.proto.notification.v1.CountUnreadRequest;
import org.profit.candle.proto.notification.v1.CountUnreadResponse;
import org.profit.candle.proto.notification.v1.GetDeliveryStatusRequest;
import org.profit.candle.proto.notification.v1.GetDeliveryStatusResponse;
import org.profit.candle.proto.notification.v1.ListNotificationsRequest;
import org.profit.candle.proto.notification.v1.ListNotificationsResponse;
import org.profit.candle.proto.notification.v1.DeleteNotificationRequest;
import org.profit.candle.proto.notification.v1.DeleteNotificationResponse;
import org.profit.candle.proto.notification.v1.MarkAllAsReadRequest;
import org.profit.candle.proto.notification.v1.MarkAllAsReadResponse;
import org.profit.candle.proto.notification.v1.MarkAsReadRequest;
import org.profit.candle.proto.notification.v1.MarkAsReadResponse;
import org.profit.candle.proto.notification.v1.NotificationServiceGrpc;
import org.profit.candle.proto.notification.v1.RegisterDeviceTokenRequest;
import org.profit.candle.proto.notification.v1.RegisterDeviceTokenResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationGrpcService
        extends NotificationServiceGrpc.NotificationServiceImplBase {

    private final NotificationService notificationService;
    private final DeviceTokenService deviceTokenService;
    private final IdempotencyExecutor idempotencyExecutor;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    @Override
    public void registerDeviceToken(
            RegisterDeviceTokenRequest request,
            StreamObserver<RegisterDeviceTokenResponse> observer
    ) {
        try {
            UUID userId = parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID);
            RegisterDeviceTokenCommand command = new RegisterDeviceTokenCommand(
                    userId,
                    requireText(request.getFcmToken()),
                    toDevicePlatform(request.getPlatform()),
                    blankToNull(request.getDeviceId()),
                    requireIdempotencyKey(request.getCommandMetadata().getIdempotencyKey())
            );

            DeviceTokenResult result = idempotencyExecutor.execute(
                    userId,
                    "RegisterDeviceToken",
                    command.idempotencyKey(),
                    requestHash(request.toBuilder()
                            .clearCommandMetadata()
                            .build()
                            .toByteArray()),
                    DeviceTokenResult.class,
                    () -> deviceTokenService.register(command)
            );

            RegisterDeviceTokenResponse response = RegisterDeviceTokenResponse.newBuilder()
                    .setDeviceTokenId(result.id().toString())
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void createNotification(
            CreateNotificationRequest request,
            StreamObserver<CreateNotificationResponse> observer
    ) {
        try {
            UUID userId = parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID);
            CreateNotificationCommand command = new CreateNotificationCommand(
                    userId,
                    toNotificationType(request.getType()),
                    requireText(request.getTitle()),
                    requireText(request.getBody()),
                    blankToNull(request.getMetaJson()),
                    requireIdempotencyKey(request.getCommandMetadata().getIdempotencyKey())
            );

            NotificationResult result = idempotencyExecutor.execute(
                    userId,
                    "CreateNotification",
                    command.idempotencyKey(),
                    requestHash(request.toBuilder()
                            .clearCommandMetadata()
                            .build()
                            .toByteArray()),
                    NotificationResult.class,
                    () -> notificationService.createAndSend(command)
            );

            CreateNotificationResponse response = CreateNotificationResponse.newBuilder()
                    .setNotification(toNotificationProto(result))
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void listNotifications(
            ListNotificationsRequest request,
            StreamObserver<ListNotificationsResponse> observer
    ) {
        try {
            ListNotificationsResult result = notificationService.list(
                    parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID),
                    request.getPageRequest().getPageSize(),
                    blankToNull(request.getPageRequest().getPageToken())
            );

            ListNotificationsResponse.Builder response = ListNotificationsResponse.newBuilder()
                    .setPageResponse(PageResponse.newBuilder()
                            .setNextPageToken(result.nextPageToken())
                            .build());
            result.notifications().forEach(notification ->
                    response.addNotifications(toNotificationProto(notification)));

            observer.onNext(response.build());
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void markAsRead(
            MarkAsReadRequest request,
            StreamObserver<MarkAsReadResponse> observer
    ) {
        try {
            UUID userId = parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID);
            UUID notificationId = parseUuid(
                    request.getNotificationId(),
                    NotificationErrorCode.INVALID_NOTIFICATION_ID
            );
            NotificationResult result = idempotencyExecutor.execute(
                    userId,
                    "MarkAsRead",
                    requireIdempotencyKey(request.getCommandMetadata().getIdempotencyKey()),
                    requestHash(request.toBuilder()
                            .clearCommandMetadata()
                    .build()
                    .toByteArray()),
                    NotificationResult.class,
                    () -> notificationService.markAsRead(
                            userId,
                            notificationId,
                            request.getCommandMetadata().getIdempotencyKey()
                    )
            );

            MarkAsReadResponse response = MarkAsReadResponse.newBuilder()
                    .setNotification(toNotificationProto(result))
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void markAllAsRead(
            MarkAllAsReadRequest request,
            StreamObserver<MarkAllAsReadResponse> observer
    ) {
        try {
            UUID userId = parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID);
            String idempotencyKey =
                    requireIdempotencyKey(request.getCommandMetadata().getIdempotencyKey());

            MarkAllAsReadResult result = idempotencyExecutor.execute(
                    userId,
                    "MarkAllAsRead",
                    idempotencyKey,
                    requestHash(request.toBuilder()
                            .clearCommandMetadata()
                            .build()
                            .toByteArray()),
                    MarkAllAsReadResult.class,
                    () -> notificationService.markAllAsRead(userId, idempotencyKey)
            );

            MarkAllAsReadResponse response = MarkAllAsReadResponse.newBuilder()
                    .setUpdatedCount(result.updatedCount())
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void deleteNotification(
            DeleteNotificationRequest request,
            StreamObserver<DeleteNotificationResponse> observer
    ) {
        try {
            UUID userId = parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID);
            UUID notificationId = parseUuid(
                    request.getNotificationId(),
                    NotificationErrorCode.INVALID_NOTIFICATION_ID
            );
            String idempotencyKey =
                    requireIdempotencyKey(request.getCommandMetadata().getIdempotencyKey());

            DeleteNotificationResult result = idempotencyExecutor.execute(
                    userId,
                    "DeleteNotification",
                    idempotencyKey,
                    requestHash(request.toBuilder()
                            .clearCommandMetadata()
                            .build()
                            .toByteArray()),
                    DeleteNotificationResult.class,
                    () -> notificationService.deleteNotification(userId, notificationId, idempotencyKey)
            );

            DeleteNotificationResponse response = DeleteNotificationResponse.newBuilder()
                    .setSuccess(result.deleted())
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void countUnread(
            CountUnreadRequest request,
            StreamObserver<CountUnreadResponse> observer
    ) {
        try {
            long unreadCount = notificationService.countUnread(
                    parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID)
            );

            CountUnreadResponse response = CountUnreadResponse.newBuilder()
                    .setUnreadCount(unreadCount)
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void getDeliveryStatus(
            GetDeliveryStatusRequest request,
            StreamObserver<GetDeliveryStatusResponse> observer
    ) {
        try {
            GetDeliveryStatusResponse.Builder response = GetDeliveryStatusResponse.newBuilder();
            notificationService.getDeliveryStatus(parseUuid(
                            request.getNotificationId(),
                            NotificationErrorCode.INVALID_NOTIFICATION_ID))
                    .forEach(delivery -> response.addDeliveries(toDeliveryProto(delivery)));

            observer.onNext(response.build());
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL
                    .withDescription(NotificationErrorCode.INTERNAL_ERROR.code())
                    .asRuntimeException());
        }
    }

    private UUID parseUuid(String value, NotificationErrorCode errorCode) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new NotificationException(errorCode, e);
        }
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new NotificationException(NotificationErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new NotificationException(NotificationErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new NotificationException(NotificationErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private DevicePlatform toDevicePlatform(
            org.profit.candle.proto.notification.v1.DevicePlatform platform
    ) {
        return switch (platform) {
            case DEVICE_PLATFORM_WEB -> DevicePlatform.WEB;
            case DEVICE_PLATFORM_ANDROID -> DevicePlatform.ANDROID;
            case DEVICE_PLATFORM_IOS -> DevicePlatform.IOS;
            default -> throw new NotificationException(
                    NotificationErrorCode.INVALID_DEVICE_PLATFORM);
        };
    }

    private NotificationType toNotificationType(
            org.profit.candle.proto.notification.v1.NotificationType type
    ) {
        return switch (type) {
            case NOTIFICATION_TYPE_PRICE_RISE -> NotificationType.PRICE_RISE;
            case NOTIFICATION_TYPE_PRICE_FALL -> NotificationType.PRICE_FALL;
            case NOTIFICATION_TYPE_BUY_FILLED -> NotificationType.BUY_FILLED;
            case NOTIFICATION_TYPE_SELL_FILLED -> NotificationType.SELL_FILLED;
            case NOTIFICATION_TYPE_MARKET_OPEN -> NotificationType.MARKET_OPEN;
            case NOTIFICATION_TYPE_MARKET_CLOSE -> NotificationType.MARKET_CLOSE;
            default -> throw new NotificationException(
                    NotificationErrorCode.INVALID_NOTIFICATION_TYPE);
        };
    }

    private org.profit.candle.proto.notification.v1.Notification toNotificationProto(
            NotificationResult result
    ) {
        var builder = org.profit.candle.proto.notification.v1.Notification.newBuilder()
                .setId(result.id().toString())
                .setUserId(result.userId().toString())
                .setType(toNotificationTypeProto(result.type()))
                .setTitle(result.title())
                .setBody(result.body())
                .setStatus(toNotificationStatusProto(result.status()))
                .setTriggeredAt(toTimestamp(result.triggeredAt()))
                .setCreatedAt(toTimestamp(result.createdAt()));

        if (result.metaJson() != null) {
            builder.setMetaJson(result.metaJson());
        }

        if (result.readAt() != null) {
            builder.setReadAt(toTimestamp(result.readAt()));
        }

        return builder.build();
    }

    private org.profit.candle.proto.notification.v1.NotificationDelivery toDeliveryProto(
            DeliveryResult result
    ) {
        var builder = org.profit.candle.proto.notification.v1.NotificationDelivery.newBuilder()
                .setId(result.id().toString())
                .setNotificationId(result.notificationId().toString())
                .setDeviceTokenId(result.deviceTokenId().toString())
                .setStatus(toDeliveryStatusProto(result.status()))
                .setCreatedAt(toTimestamp(result.createdAt()));

        if (result.fcmMessageId() != null) {
            builder.setFcmMessageId(result.fcmMessageId());
        }

        if (result.errorMessage() != null) {
            builder.setErrorMessage(result.errorMessage());
        }

        if (result.sentAt() != null) {
            builder.setSentAt(toTimestamp(result.sentAt()));
        }

        return builder.build();
    }

    private org.profit.candle.proto.notification.v1.NotificationType toNotificationTypeProto(
            NotificationType type
    ) {
        return switch (type) {
            case PRICE_RISE -> org.profit.candle.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_PRICE_RISE;
            case PRICE_FALL -> org.profit.candle.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_PRICE_FALL;
            case BUY_FILLED -> org.profit.candle.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_BUY_FILLED;
            case SELL_FILLED -> org.profit.candle.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_SELL_FILLED;
            case MARKET_OPEN -> org.profit.candle.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_MARKET_OPEN;
            case MARKET_CLOSE -> org.profit.candle.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_MARKET_CLOSE;
        };
    }

    private org.profit.candle.proto.notification.v1.NotificationStatus toNotificationStatusProto(
            NotificationStatus status
    ) {
        return switch (status) {
            case UNREAD -> org.profit.candle.proto.notification.v1.NotificationStatus.NOTIFICATION_STATUS_UNREAD;
            case READ -> org.profit.candle.proto.notification.v1.NotificationStatus.NOTIFICATION_STATUS_READ;
        };
    }

    private org.profit.candle.proto.notification.v1.DeliveryStatus toDeliveryStatusProto(
            DeliveryStatus status
    ) {
        return switch (status) {
            case PENDING -> org.profit.candle.proto.notification.v1.DeliveryStatus.DELIVERY_STATUS_PENDING;
            case SENT -> org.profit.candle.proto.notification.v1.DeliveryStatus.DELIVERY_STATUS_SENT;
            case FAILED -> org.profit.candle.proto.notification.v1.DeliveryStatus.DELIVERY_STATUS_FAILED;
        };
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private String requestHash(byte[] requestBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(requestBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new NotificationException(NotificationErrorCode.INVALID_REQUEST, e);
        }
    }

    private Status toGrpcStatus(NotificationException e) {
        String code = e.errorCode().code();

        if (code.equals(NotificationErrorCode.NOTIFICATION_NOT_FOUND.code())) {
            return Status.NOT_FOUND.withDescription(code);
        }

        if (code.equals(NotificationErrorCode.DEVICE_TOKEN_NOT_FOUND.code())) {
            return Status.NOT_FOUND.withDescription(code);
        }

        if (code.equals(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED.code())) {
            return Status.PERMISSION_DENIED.withDescription(code);
        }

        if (code.equals(NotificationErrorCode.IDEMPOTENCY_REQUEST_MISMATCH.code())) {
            return Status.ALREADY_EXISTS.withDescription(code);
        }

        if (code.equals(NotificationErrorCode.FCM_SEND_FAILED.code())) {
            return Status.UNAVAILABLE.withDescription(code);
        }

        if (code.equals(NotificationErrorCode.FIREBASE_CREDENTIAL_INVALID.code())) {
            return Status.UNAVAILABLE.withDescription(code);
        }

        return Status.INVALID_ARGUMENT.withDescription(code);
    }
}
