package org.profit.candle.trading.order.exception;

import org.profit.candle.common.error.CandleException;

public class OrderException extends CandleException {

    public OrderException(OrderErrorCode errorCode) {
        super(errorCode);
    }

    public OrderException(OrderErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
