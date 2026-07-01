package org.profit.candle.wishlist.wishlist.exception;

import org.profit.candle.common.error.ErrorCode;

public enum WishlistErrorCode implements ErrorCode {
    INVALID_USER_ID("WISHLIST_INVALID_USER_ID", "사용자 식별자가 올바르지 않습니다."),
    INVALID_SYMBOL("WISHLIST_INVALID_SYMBOL", "종목 코드가 올바르지 않습니다."),
    INVALID_IDEMPOTENCY_KEY("WISHLIST_INVALID_IDEMPOTENCY_KEY", "멱등성 키가 올바르지 않습니다."),
    ITEM_NOT_FOUND("WISHLIST_ITEM_NOT_FOUND", "관심종목을 찾을 수 없습니다."),
    INTERNAL_ERROR("WISHLIST_INTERNAL_ERROR", "관심종목 요청을 처리할 수 없습니다.");

    private final String code;
    private final String message;

    WishlistErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
