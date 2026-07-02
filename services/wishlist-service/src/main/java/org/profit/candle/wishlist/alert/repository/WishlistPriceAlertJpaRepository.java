package org.profit.candle.wishlist.alert.repository;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import org.profit.candle.wishlist.alert.entity.AlertDirection;
import org.profit.candle.wishlist.alert.entity.WishlistPriceAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistPriceAlertJpaRepository extends JpaRepository<WishlistPriceAlert, UUID> {
    List<WishlistPriceAlert> findByNotificationIdIsNullOrderByCreatedAtAsc(Pageable pageable);

    boolean existsByUserIdAndSymbolAndTradingDateAndDirectionAndThresholdBasisPoints(
            UUID userId,
            String symbol,
            LocalDate tradingDate,
            AlertDirection direction,
            int thresholdBasisPoints
    );
}
