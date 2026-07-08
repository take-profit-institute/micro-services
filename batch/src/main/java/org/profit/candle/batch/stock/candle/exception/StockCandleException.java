package org.profit.candle.batch.stock.candle.exception;

import org.profit.candle.common.error.CandleException;

public class StockCandleException extends CandleException {

    public StockCandleException(StockCandleErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public boolean retryable() {
        return ((StockCandleErrorCode) errorCode()).retryable();
    }
}
