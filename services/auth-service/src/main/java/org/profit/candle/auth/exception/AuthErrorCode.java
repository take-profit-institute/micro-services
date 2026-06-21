package org.profit.candle.auth.exception;

import org.profit.candle.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_OAUTH_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_OAUTH_REQUEST", "OAuth 인증 요청이 올바르지 않습니다."),
    GOOGLE_ACCOUNT_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH_GOOGLE_ACCOUNT_NOT_VERIFIED", "Google 계정 인증에 실패했습니다."),
    GOOGLE_OAUTH_EXCHANGE_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_GOOGLE_OAUTH_EXCHANGE_FAILED", "Google 인증 정보를 확인할 수 없습니다."),
    GOOGLE_OAUTH_CONFIGURATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_GOOGLE_OAUTH_CONFIGURATION_INVALID", "인증 서비스 설정이 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "Refresh token이 유효하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
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
