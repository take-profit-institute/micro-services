package org.profit.candle.news.naver.exception;

public enum NaverNewsApiFailureReason {
    INVALID_REQUEST("NAVER_INVALID_REQUEST"),
    AUTHORIZATION_FAILED("NAVER_AUTHORIZATION_FAILED"),
    RATE_LIMITED("NAVER_RATE_LIMITED"),
    SERVER_ERROR("NAVER_SERVER_ERROR"),
    REQUEST_FAILED("NAVER_REQUEST_FAILED"),
    EMPTY_RESPONSE("NAVER_EMPTY_RESPONSE");

    private final String message;

    NaverNewsApiFailureReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
