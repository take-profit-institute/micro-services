package org.profit.candle.notification.notification.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.UUID;
import org.profit.candle.notification.delivery.entity.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.device.dto.DeviceTokenResult;
import org.profit.candle.notification.device.dto.RegisterDeviceTokenCommand;
import org.profit.candle.notification.device.entity.DevicePlatform;
import org.profit.candle.notification.device.service.DeviceTokenService;
import org.profit.candle.notification.notification.dto.CreateNotificationCommand;
import org.profit.candle.notification.notification.dto.DeliveryResult;
import org.profit.candle.notification.notification.dto.ListNotificationsResult;
import org.profit.candle.notification.notification.dto.NotificationResult;
import org.profit.candle.notification.notification.entity.NotificationStatus;
import org.profit.candle.notification.notification.entity.NotificationType;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.profit.candle.notification.notification.service.NotificationService;
import org.profit.candle.proto.notification.v1.CreateNotificationRequest;
import org.profit.candle.proto.notification.v1.CreateNotificationResponse;
import org.profit.candle.proto.notification.v1.CountUnreadRequest;
import org.profit.candle.proto.notification.v1.CountUnreadResponse;
import org.profit.candle.proto.notification.v1.GetDeliveryStatusRequest;
import org.profit.candle.proto.notification.v1.GetDeliveryStatusResponse;
import org.profit.candle.proto.notification.v1.ListNotificationsRequest;
import org.profit.candle.proto.notification.v1.ListNotificationsResponse;
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

    @Override
    public void registerDeviceToken(
            RegisterDeviceTokenRequest request,
            StreamObserver<RegisterDeviceTokenResponse> observer
    ) {
        try {
            RegisterDeviceTokenCommand command = new RegisterDeviceTokenCommand(
                    parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID),
                    requireText(request.getFcmToken()),
                    toDevicePlatform(request.getPlatform()),
                    blankToNull(request.getDeviceId())
            );

            DeviceTokenResult result = deviceTokenService.register(command);

            RegisterDeviceTokenResponse response = RegisterDeviceTokenResponse.newBuilder()
                    .setDeviceTokenId(result.id().toString())
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(NotificationErrorCode.INVALID_REQUEST.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void createNotification(
            CreateNotificationRequest request,
            StreamObserver<CreateNotificationResponse> observer
    ) {
        try {
            CreateNotificationCommand command = new CreateNotificationCommand(
                    parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID),
                    toNotificationType(request.getType()),
                    requireText(request.getTitle()),
                    requireText(request.getBody()),
                    blankToNull(request.getMetaJson()),
                    blankToNull(request.getIdempotencyKey())
            );

            NotificationResult result = notificationService.createAndSend(command);

            CreateNotificationResponse response = CreateNotificationResponse.newBuilder()
                    .setNotification(toNotificationProto(result))
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(NotificationErrorCode.INVALID_REQUEST.code())
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
                    request.getPageSize(),
                    blankToNull(request.getPageToken())
            );

            ListNotificationsResponse.Builder response = ListNotificationsResponse.newBuilder()
                    .setNextPageToken(result.nextPageToken());
            result.notifications().forEach(notification ->
                    response.addNotifications(toNotificationProto(notification)));

            observer.onNext(response.build());
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(NotificationErrorCode.INVALID_REQUEST.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void markAsRead(
            MarkAsReadRequest request,
            StreamObserver<MarkAsReadResponse> observer
    ) {
        try {
            NotificationResult result = notificationService.markAsRead(
                    parseUuid(request.getUserId(), NotificationErrorCode.INVALID_USER_ID),
                    parseUuid(request.getNotificationId(),
                            NotificationErrorCode.INVALID_NOTIFICATION_ID)
            );

            MarkAsReadResponse response = MarkAsReadResponse.newBuilder()
                    .setNotification(toNotificationProto(result))
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (NotificationException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(NotificationErrorCode.INVALID_REQUEST.code())
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
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(NotificationErrorCode.INVALID_REQUEST.code())
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
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(NotificationErrorCode.INVALID_REQUEST.code())
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

    private Status toGrpcStatus(NotificationException e) {
        String code = e.errorCode().code();

        if (code.equals(NotificationErrorCode.NOTIFICATION_NOT_FOUND.code())) {
            return Status.NOT_FOUND.withDescription(code);
        }

        if (code.equals(NotificationErrorCode.FCM_SEND_FAILED.code())) {
            return Status.UNAVAILABLE.withDescription(code);
        }

        return Status.INVALID_ARGUMENT.withDescription(code);
    }
}
