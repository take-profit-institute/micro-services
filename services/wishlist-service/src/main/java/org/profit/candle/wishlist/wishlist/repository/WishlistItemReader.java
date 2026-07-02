package org.profit.candle.wishlist.wishlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.wishlist.wishlist.entity.WishlistItem;

public interface WishlistItemReader {
    Optional<WishlistItem> findActive(UUID userId, String symbol);

    List<WishlistItem> listActive(UUID userId, int limit, int offset);

    List<WishlistItem> listActiveBySymbol(String symbol);
}
