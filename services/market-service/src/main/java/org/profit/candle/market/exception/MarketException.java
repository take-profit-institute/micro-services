package org.profit.candle.market.exception;

import org.profit.candle.common.error.CandleException;

public class MarketException extends CandleException {

    public MarketException(MarketErrorCode errorCode) {
        super(errorCode);
    }

    public MarketException(MarketErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
