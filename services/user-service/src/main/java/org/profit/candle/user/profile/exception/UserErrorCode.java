package org.profit.candle.user.profile.exception;

import org.profit.candle.common.error.ErrorCode;

public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "이미 가입된 사용자입니다."),
    NICKNAME_TOO_LONG("USER_NICKNAME_TOO_LONG", "닉네임은 50자 이하여야 합니다."),
    PROFILE_IMAGE_URL_TOO_LONG("USER_PROFILE_IMAGE_URL_TOO_LONG", "프로필 이미지 URL은 500자 이하여야 합니다.");

    private final String code;
    private final String message;

    UserErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() { return code; }

    @Override
    public String message() { return message; }
}
