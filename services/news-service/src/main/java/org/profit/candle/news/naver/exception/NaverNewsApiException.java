package org.profit.candle.news.naver.exception;

public class NaverNewsApiException extends RuntimeException {
    private final NaverNewsApiFailureReason reason;

    public NaverNewsApiException(NaverNewsApiFailureReason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public NaverNewsApiException(NaverNewsApiFailureReason reason, Throwable cause) {
        super(reason.message(), cause);
        this.reason = reason;
    }

    public NaverNewsApiFailureReason reason() {
        return reason;
    }
}
