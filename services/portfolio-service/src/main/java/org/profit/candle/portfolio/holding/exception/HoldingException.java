package org.profit.candle.portfolio.holding.exception;

import org.profit.candle.common.error.CandleException;

public class HoldingException extends CandleException {

    public HoldingException(HoldingErrorCode errorCode) {
        super(errorCode);
    }
}
