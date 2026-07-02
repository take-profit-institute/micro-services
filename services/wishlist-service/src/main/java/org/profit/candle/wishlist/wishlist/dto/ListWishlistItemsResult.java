package org.profit.candle.wishlist.wishlist.dto;

import java.util.List;

public record ListWishlistItemsResult(
        List<WishlistItemResult> items,
        String nextPageToken
) {
}
