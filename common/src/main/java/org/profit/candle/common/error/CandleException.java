package org.profit.candle.common.error;

/**
 * 서비스별 ErrorCode를 클라이언트 응답과 로그에 일관되게 전달하는 기본 예외다.
 * HTTP/gRPC 상태 변환은 각 전송 계층에서 담당한다.
 */
public class CandleException extends RuntimeException {
    private final ErrorCode errorCode;

    public CandleException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public CandleException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
