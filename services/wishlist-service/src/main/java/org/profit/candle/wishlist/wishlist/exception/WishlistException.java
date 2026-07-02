package org.profit.candle.wishlist.wishlist.exception;

import org.profit.candle.common.error.CandleException;

public class WishlistException extends CandleException {
    public WishlistException(WishlistErrorCode errorCode) {
        super(errorCode);
    }

    public WishlistException(WishlistErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
