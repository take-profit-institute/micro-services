package org.profit.candle.batch.stock.sync.exception;

import org.profit.candle.common.error.ErrorCode;

public enum StockSyncErrorCode implements ErrorCode {
    EXTERNAL_CLIENT_FAILED(
            "BATCH_STOCK_SYNC_EXTERNAL_CLIENT_FAILED",
            "Stock Service 종목 동기화 호출에 실패했습니다.",
            false
    ),
    EXTERNAL_CLIENT_RETRYABLE(
            "BATCH_STOCK_SYNC_EXTERNAL_CLIENT_RETRYABLE",
            "재시도 가능한 Stock Service 오류가 발생했습니다.",
            true
    );

    private final String code;
    private final String message;
    private final boolean retryable;

    StockSyncErrorCode(String code, String message, boolean retryable) {
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
