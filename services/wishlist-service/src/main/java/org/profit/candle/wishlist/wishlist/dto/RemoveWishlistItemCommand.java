package org.profit.candle.wishlist.wishlist.dto;

import java.util.UUID;

public record RemoveWishlistItemCommand(
        UUID userId,
        String symbol,
        String idempotencyKey
) {
}
