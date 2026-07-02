package org.profit.candle.wishlist.alert.repository;

import org.profit.candle.wishlist.alert.entity.MarketOpenSnapshot;
import org.profit.candle.wishlist.alert.entity.WishlistPriceAlert;

public interface PriceAlertWriter {
    MarketOpenSnapshot save(MarketOpenSnapshot snapshot);

    WishlistPriceAlert saveAlert(WishlistPriceAlert alert);
}
