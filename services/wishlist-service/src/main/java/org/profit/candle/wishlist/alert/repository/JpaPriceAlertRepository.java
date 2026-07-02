package org.profit.candle.wishlist.alert.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.wishlist.alert.entity.AlertDirection;
import org.profit.candle.wishlist.alert.entity.MarketOpenSnapshot;
import org.profit.candle.wishlist.alert.entity.WishlistPriceAlert;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaPriceAlertRepository implements PriceAlertReader, PriceAlertWriter {
    private final MarketOpenSnapshotJpaRepository snapshotRepository;
    private final WishlistPriceAlertJpaRepository alertRepository;

    @Override
    public Optional<MarketOpenSnapshot> findSnapshot(String symbol, LocalDate tradingDate) {
        return snapshotRepository.findBySymbolAndTradingDate(symbol, tradingDate);
    }

    @Override
    public List<WishlistPriceAlert> listPending(int limit) {
        return alertRepository.findByNotificationIdIsNullOrderByCreatedAtAsc(PageRequest.of(0, limit));
    }

    @Override
    public Optional<WishlistPriceAlert> findAlert(UUID alertId) {
        return alertRepository.findById(alertId);
    }

    @Override
    public boolean alertExists(
            UUID userId,
            String symbol,
            LocalDate tradingDate,
            AlertDirection direction,
            int thresholdBasisPoints
    ) {
        return alertRepository.existsByUserIdAndSymbolAndTradingDateAndDirectionAndThresholdBasisPoints(
                userId,
                symbol,
                tradingDate,
                direction,
                thresholdBasisPoints
        );
    }

    @Override
    public MarketOpenSnapshot save(MarketOpenSnapshot snapshot) {
        return snapshotRepository.save(snapshot);
    }

    @Override
    public WishlistPriceAlert saveAlert(WishlistPriceAlert alert) {
        return alertRepository.saveAndFlush(alert);
    }
}
