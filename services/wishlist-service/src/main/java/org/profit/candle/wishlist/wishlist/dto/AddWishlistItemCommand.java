package org.profit.candle.wishlist.wishlist.dto;

import java.util.UUID;

public record AddWishlistItemCommand(
        UUID userId,
        String symbol,
        String displayName,
        String market,
        String idempotencyKey
) {
}
