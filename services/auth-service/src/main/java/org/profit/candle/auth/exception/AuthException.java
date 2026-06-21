package org.profit.candle.auth.exception;

import org.profit.candle.common.error.CandleException;

public class AuthException extends CandleException {
    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(AuthErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    @Override
    public AuthErrorCode errorCode() {
        return (AuthErrorCode) super.errorCode();
    }
}
