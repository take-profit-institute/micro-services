package org.profit.candle.learning.exception;

import org.profit.candle.common.error.CandleException;

public class ContentNotFoundException extends CandleException {

    public ContentNotFoundException() {
        super(LearningErrorCode.CONTENT_NOT_FOUND);
    }
}