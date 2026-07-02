package org.profit.candle.wishlist.wishlist.service;

import java.util.UUID;
import org.profit.candle.wishlist.wishlist.dto.AddWishlistItemCommand;
import org.profit.candle.wishlist.wishlist.dto.ListWishlistItemsResult;
import org.profit.candle.wishlist.wishlist.dto.RemoveWishlistItemCommand;
import org.profit.candle.wishlist.wishlist.dto.WishlistItemResult;

public interface WishlistService {
    WishlistItemResult add(AddWishlistItemCommand command);

    void remove(RemoveWishlistItemCommand command);

    ListWishlistItemsResult list(UUID userId, int pageSize, String pageToken);
}
