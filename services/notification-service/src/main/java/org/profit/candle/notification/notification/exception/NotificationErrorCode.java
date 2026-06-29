package org.profit.candle.notification.notification.exception;

import org.profit.candle.common.error.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {

    INVALID_USER_ID("NOTIFICATION_INVALID_USER_ID", "Invalid user ID."),
    INVALID_NOTIFICATION_ID("NOTIFICATION_INVALID_NOTIFICATION_ID", "Invalid notification ID."),
    INVALID_DEVICE_PLATFORM("NOTIFICATION_INVALID_DEVICE_PLATFORM", "Invalid device platform."),
    INVALID_NOTIFICATION_TYPE("NOTIFICATION_INVALID_NOTIFICATION_TYPE", "Invalid notification type."),
    INVALID_REQUEST("NOTIFICATION_INVALID_REQUEST", "Invalid notification request."),
    INVALID_IDEMPOTENCY_KEY("NOTIFICATION_INVALID_IDEMPOTENCY_KEY", "Invalid idempotency key."),
    IDEMPOTENCY_KEY_REQUIRED("NOTIFICATION_IDEMPOTENCY_KEY_REQUIRED", "Idempotency key is required."),
    IDEMPOTENCY_REQUEST_MISMATCH("NOTIFICATION_IDEMPOTENCY_REQUEST_MISMATCH", "Idempotency request hash does not match."),
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", "Notification not found."),
    NOTIFICATION_ACCESS_DENIED("NOTIFICATION_ACCESS_DENIED", "Notification access denied."),
    DEVICE_TOKEN_NOT_FOUND("NOTIFICATION_DEVICE_TOKEN_NOT_FOUND", "Device token not found."),
    FCM_SEND_FAILED("NOTIFICATION_FCM_SEND_FAILED", "Failed to send FCM notification."),
    FIREBASE_CREDENTIAL_INVALID("NOTIFICATION_FIREBASE_CREDENTIAL_INVALID", "Firebase credential is invalid."),
    INTERNAL_ERROR("NOTIFICATION_INTERNAL_ERROR", "Internal notification service error.");

    private final String code;
    private final String message;

    NotificationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
