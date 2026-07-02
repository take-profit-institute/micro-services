package org.profit.candle.batch.portfolio.eod.exception;

import org.profit.candle.common.error.CandleException;

public class EodBatchException extends CandleException {

    public EodBatchException(EodBatchErrorCode errorCode) {
        super(errorCode);
    }

    public EodBatchException(EodBatchErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public boolean retryable() {
        return ((EodBatchErrorCode) errorCode()).retryable();
    }
}
