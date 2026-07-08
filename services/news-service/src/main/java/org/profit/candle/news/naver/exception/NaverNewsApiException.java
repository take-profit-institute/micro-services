package org.profit.candle.news.naver.exception;

public class NaverNewsApiException extends RuntimeException {
    private final NaverNewsApiFailureReason reason;
    private final Integer statusCode;
    private final String responseBodySnippet;
    private final String naverErrorCode;

    public NaverNewsApiException(NaverNewsApiFailureReason reason) {
        this(reason, null, null, null, null);
    }

    public NaverNewsApiException(NaverNewsApiFailureReason reason, Throwable cause) {
        this(reason, null, null, null, cause);
    }

    public NaverNewsApiException(
            NaverNewsApiFailureReason reason,
            Integer statusCode,
            String responseBodySnippet,
            String naverErrorCode,
            Throwable cause
    ) {
        super(reason.message(), cause);
        this.reason = reason;
        this.statusCode = statusCode;
        this.responseBodySnippet = responseBodySnippet;
        this.naverErrorCode = naverErrorCode;
    }

    public NaverNewsApiFailureReason reason() {
        return reason;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public String responseBodySnippet() {
        return responseBodySnippet;
    }

    public String naverErrorCode() {
        return naverErrorCode;
    }
}
