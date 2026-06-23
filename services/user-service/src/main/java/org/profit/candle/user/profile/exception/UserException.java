package org.profit.candle.user.profile.exception;

import org.profit.candle.common.error.CandleException;

public class UserException extends CandleException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    public UserException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
