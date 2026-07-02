package org.profit.candle.wishlist.alert.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.wishlist.alert.entity.MarketOpenSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketOpenSnapshotJpaRepository extends JpaRepository<MarketOpenSnapshot, UUID> {
    Optional<MarketOpenSnapshot> findBySymbolAndTradingDate(String symbol, LocalDate tradingDate);
}
