package org.profit.candle.batch.trading.exception;

import org.profit.candle.common.error.CandleException;

public class TradingBatchException extends CandleException {

    public TradingBatchException(TradingBatchErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public boolean retryable() {
        return ((TradingBatchErrorCode) errorCode()).retryable();
    }
}
