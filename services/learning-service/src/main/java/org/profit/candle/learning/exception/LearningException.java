package org.profit.candle.learning.exception;

import org.profit.candle.common.error.CandleException;
import org.profit.candle.common.error.ErrorCode;

public class LearningException extends CandleException {
    public LearningException(LearningErrorCode errorCode) {
        super(errorCode);
    }
}