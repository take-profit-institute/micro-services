package org.profit.candle.wishlist.alert.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.wishlist.alert.entity.AlertDirection;
import org.profit.candle.wishlist.alert.entity.MarketOpenSnapshot;
import org.profit.candle.wishlist.alert.entity.WishlistPriceAlert;

public interface PriceAlertReader {
    Optional<MarketOpenSnapshot> findSnapshot(String symbol, LocalDate tradingDate);

    List<WishlistPriceAlert> listPending(int limit);

    Optional<WishlistPriceAlert> findAlert(UUID alertId);

    boolean alertExists(UUID userId, String symbol, LocalDate tradingDate, AlertDirection direction, int thresholdBasisPoints);
}
