package org.profit.candle.trading.account.exception;

import org.profit.candle.common.error.CandleException;

public class AccountException extends CandleException {

    public AccountException(AccountErrorCode errorCode) {
        super(errorCode);
    }

    public AccountException(AccountErrorCode errorCode, Throwable cause){
        super(errorCode, cause);
    }
}
