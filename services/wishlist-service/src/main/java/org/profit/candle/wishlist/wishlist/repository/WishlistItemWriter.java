package org.profit.candle.wishlist.wishlist.repository;

import org.profit.candle.wishlist.wishlist.entity.WishlistItem;

public interface WishlistItemWriter {
    WishlistItem save(WishlistItem item);
}
