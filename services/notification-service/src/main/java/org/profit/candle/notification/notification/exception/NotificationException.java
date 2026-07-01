package org.profit.candle.notification.notification.exception;


import org.profit.candle.common.error.CandleException;

public class NotificationException extends CandleException {

    public NotificationException(NotificationErrorCode errorCode) {
        super(errorCode);
    }

    public NotificationException(NotificationErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}