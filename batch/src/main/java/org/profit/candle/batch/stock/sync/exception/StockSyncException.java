package org.profit.candle.batch.stock.sync.exception;

import org.profit.candle.common.error.CandleException;

public class StockSyncException extends CandleException {

    public StockSyncException(StockSyncErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public boolean retryable() {
        return ((StockSyncErrorCode) errorCode()).retryable();
    }
}
