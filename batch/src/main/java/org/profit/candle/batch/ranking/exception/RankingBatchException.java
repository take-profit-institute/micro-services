package org.profit.candle.batch.ranking.exception;

import org.profit.candle.common.error.CandleException;

public class RankingBatchException extends CandleException {

    public RankingBatchException(RankingBatchErrorCode errorCode) {
        super(errorCode);
    }

    public RankingBatchException(RankingBatchErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public boolean retryable() {
        return ((RankingBatchErrorCode) errorCode()).retryable();
    }
}
