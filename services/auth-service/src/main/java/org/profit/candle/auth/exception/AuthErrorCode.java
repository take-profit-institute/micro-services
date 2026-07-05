package org.profit.candle.auth.exception;

import org.profit.candle.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_OAUTH_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_OAUTH_REQUEST", "OAuth 인증 요청이 올바르지 않습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_UNSUPPORTED_OAUTH_PROVIDER", "지원하지 않는 OAuth 제공자입니다."),
    OAUTH_ACCOUNT_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH_OAUTH_ACCOUNT_NOT_VERIFIED", "OAuth 계정 인증에 실패했습니다."),
    GOOGLE_OAUTH_EXCHANGE_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_GOOGLE_OAUTH_EXCHANGE_FAILED", "Google 인증 정보를 확인할 수 없습니다."),
    GOOGLE_OAUTH_CONFIGURATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_GOOGLE_OAUTH_CONFIGURATION_INVALID", "인증 서비스 설정이 올바르지 않습니다."),
    KAKAO_OAUTH_EXCHANGE_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_KAKAO_OAUTH_EXCHANGE_FAILED", "Kakao 인증 정보를 확인할 수 없습니다."),
    KAKAO_OAUTH_CONFIGURATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_KAKAO_OAUTH_CONFIGURATION_INVALID", "인증 서비스 설정이 올바르지 않습니다."),
    NAVER_OAUTH_EXCHANGE_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_NAVER_OAUTH_EXCHANGE_FAILED", "Naver 인증 정보를 확인할 수 없습니다."),
    NAVER_OAUTH_CONFIGURATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_NAVER_OAUTH_CONFIGURATION_INVALID", "인증 서비스 설정이 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "Refresh token이 유효하지 않습니다."),
    INVALID_AUTH_USER_ID(HttpStatus.BAD_REQUEST, "AUTH_INVALID_USER_ID", "Invalid auth user id."),
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_USER_NOT_FOUND", "Auth user was not found."),
    INVALID_ADMIN_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_INVALID_ADMIN_REQUEST", "관리자 요청이 올바르지 않습니다."),
    INVALID_ADMIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_ADMIN_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."),
    ADMIN_ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH_ADMIN_ACCOUNT_LOCKED", "로그인 시도가 많아 계정이 잠겼습니다. 잠시 후 다시 시도해 주세요."),
    ADMIN_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "AUTH_ADMIN_ACCOUNT_DISABLED", "비활성화된 관리자 계정입니다."),
    ADMIN_USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_ADMIN_USERNAME_ALREADY_EXISTS", "이미 사용 중인 관리자 아이디입니다."),
    ADMIN_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_ADMIN_ACCOUNT_NOT_FOUND", "관리자 계정을 찾을 수 없습니다."),
    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_ADMIN_FORBIDDEN", "해당 작업을 수행할 권한이 없습니다.");

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
