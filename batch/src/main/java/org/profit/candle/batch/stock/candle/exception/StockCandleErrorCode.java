package org.profit.candle.batch.stock.candle.exception;

import org.profit.candle.common.error.ErrorCode;

public enum StockCandleErrorCode implements ErrorCode {
    EXTERNAL_CLIENT_FAILED(
            "BATCH_STOCK_CANDLE_EXTERNAL_CLIENT_FAILED",
            "Stock Service 캔들 적재 호출에 실패했습니다.",
            false
    ),
    EXTERNAL_CLIENT_RETRYABLE(
            "BATCH_STOCK_CANDLE_EXTERNAL_CLIENT_RETRYABLE",
            "재시도 가능한 Stock Service 오류가 발생했습니다.",
            true
    ),
    FAILURE_LIMIT_EXCEEDED(
            "BATCH_STOCK_CANDLE_FAILURE_LIMIT_EXCEEDED",
            "캔들 적재 실패 종목이 누적 상한을 초과했습니다.",
            false
    );

    private final String code;
    private final String message;
    private final boolean retryable;

    StockCandleErrorCode(String code, String message, boolean retryable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }
}
