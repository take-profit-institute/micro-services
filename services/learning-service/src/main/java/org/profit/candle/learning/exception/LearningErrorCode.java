package org.profit.candle.learning.exception;

import org.profit.candle.common.error.ErrorCode;

public enum LearningErrorCode implements ErrorCode {

    CONTENT_NOT_FOUND("LEARNING_001", "콘텐츠를 찾을 수 없습니다"),
    CONTENT_ALREADY_DELETED("LEARNING_002", "이미 삭제된 콘텐츠입니다"),
    INVALID_PROGRESS("LEARNING_003", "진도율은 0~100 사이여야 합니다"),
    IDEMPOTENCY_KEY_REQUIRED("LEARNING_004", "idempotency_key는 필수입니다"),
    IDEMPOTENCY_REQUEST_MISMATCH("LEARNING_005", "동일 idempotency_key에 다른 요청이 전달되었습니다"),
    INTERNAL_ERROR("LEARNING_999", "내부 오류가 발생했습니다");

    private final String code;
    private final String message;

    LearningErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() { return code; }

    @Override
    public String message() { return message; }
}