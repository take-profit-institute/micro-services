package org.profit.candle.trading.reservation.exception;

import org.profit.candle.common.error.CandleException;

public class ReservationException extends CandleException {

    public ReservationException(ReservationErrorCode errorCode) {
        super(errorCode);
    }

    public ReservationException(ReservationErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
