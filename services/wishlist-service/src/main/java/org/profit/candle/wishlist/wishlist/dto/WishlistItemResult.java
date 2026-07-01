package org.profit.candle.wishlist.wishlist.dto;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResult(
        UUID id,
        UUID userId,
        String symbol,
        String displayName,
        String market,
        Instant createdAt
) {
}
