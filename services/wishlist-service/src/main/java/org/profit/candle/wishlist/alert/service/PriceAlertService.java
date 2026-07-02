package org.profit.candle.wishlist.alert.service;

import io.grpc.StatusRuntimeException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.wishlist.alert.dto.PriceAlertCandidate;
import org.profit.candle.wishlist.alert.entity.AlertDirection;
import org.profit.candle.wishlist.alert.entity.MarketOpenSnapshot;
import org.profit.candle.wishlist.alert.entity.WishlistPriceAlert;
import org.profit.candle.wishlist.alert.repository.PriceAlertReader;
import org.profit.candle.wishlist.alert.repository.PriceAlertWriter;
import org.profit.candle.wishlist.config.WishlistProperties;
import org.profit.candle.wishlist.market.dto.QuoteTick;
import org.profit.candle.wishlist.notification.client.NotificationClient;
import org.profit.candle.wishlist.notification.client.PriceAlertNotificationCommand;
import org.profit.candle.wishlist.wishlist.entity.WishlistItem;
import org.profit.candle.wishlist.wishlist.repository.WishlistItemReader;
import org.profit.candle.wishlist.wishlist.service.DefaultWishlistService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertService {
    private final WishlistProperties properties;
    private final WishlistItemReader wishlistItemReader;
    private final PriceAlertReader alertReader;
    private final PriceAlertWriter alertWriter;
    private final NotificationClient notificationClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void evaluate(QuoteTick tick) {
        if (tick == null || !tick.open()) {
            return;
        }
        String symbol = DefaultWishlistService.normalizeSymbol(tick.symbol());
        if (tick.price() <= 0 || tick.openPrice() <= 0 || tick.tradingDate() == null) {
            throw new IllegalArgumentException("quote tick has invalid price or trading date");
        }

        int changeBasisPoints = PriceChangeCalculator.basisPoints(tick.openPrice(), tick.price());
        AlertDirection direction = PriceChangeCalculator.direction(
                changeBasisPoints,
                properties.alert().thresholdBasisPoints()
        );
        if (direction == null) {
            transactionTemplate.executeWithoutResult(status -> observeSnapshot(
                    symbol,
                    tick,
                    changeBasisPoints,
                    clock.instant()
            ));
            return;
        }

        List<PriceAlertCandidate> candidates = transactionTemplate.execute(status ->
                prepareCandidates(symbol, tick, changeBasisPoints, direction, clock.instant())
        );
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        candidates.forEach(this::sendNotification);
    }

    @Scheduled(fixedDelayString = "${wishlist.alert.retry-delay:PT30S}")
    public void retryPendingNotifications() {
        List<PriceAlertCandidate> candidates = transactionTemplate.execute(status ->
                alertReader.listPending(properties.alert().retryBatchSize())
                        .stream()
                        .map(this::toCandidate)
                        .toList()
        );
        if (candidates == null) {
            return;
        }
        candidates.forEach(this::sendNotification);
    }

    private List<PriceAlertCandidate> prepareCandidates(
            String symbol,
            QuoteTick tick,
            int changeBasisPoints,
            AlertDirection direction,
            Instant now
    ) {
        observeSnapshot(symbol, tick, changeBasisPoints, now);
        return wishlistItemReader.listActiveBySymbol(symbol)
                .stream()
                .map(item -> insertAlert(item, tick, changeBasisPoints, direction, now))
                .filter(candidate -> candidate != null)
                .toList();
    }

    private void observeSnapshot(String symbol, QuoteTick tick, int changeBasisPoints, Instant now) {
        MarketOpenSnapshot snapshot = alertReader.findSnapshot(symbol, tick.tradingDate())
                .orElseGet(() -> MarketOpenSnapshot.open(symbol, tick.tradingDate(), tick.openPrice(), now));
        snapshot.observe(tick.price(), changeBasisPoints, now);
        alertWriter.save(snapshot);
    }

    private PriceAlertCandidate insertAlert(
            WishlistItem item,
            QuoteTick tick,
            int changeBasisPoints,
            AlertDirection direction,
            Instant now
    ) {
        if (alertReader.alertExists(
                item.getUserId(),
                item.getSymbol(),
                tick.tradingDate(),
                direction,
                properties.alert().thresholdBasisPoints()
        )) {
            return null;
        }
        String idempotencyKey = idempotencyKey(
                item.getUserId(),
                item.getSymbol(),
                tick.tradingDate(),
                direction,
                properties.alert().thresholdBasisPoints()
        );
        WishlistPriceAlert alert = WishlistPriceAlert.trigger(
                item.getUserId(),
                item.getSymbol(),
                tick.tradingDate(),
                direction,
                properties.alert().thresholdBasisPoints(),
                tick.openPrice(),
                tick.price(),
                changeBasisPoints,
                idempotencyKey,
                now
        );
        try {
            return toCandidate(alertWriter.saveAlert(alert));
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    private void sendNotification(PriceAlertCandidate candidate) {
        try {
            UUID notificationId = notificationClient.createPriceAlertNotification(
                    new PriceAlertNotificationCommand(
                            candidate.userId(),
                            candidate.symbol(),
                            candidate.tradingDate(),
                            candidate.direction(),
                            candidate.thresholdBasisPoints(),
                            candidate.openPrice(),
                            candidate.triggerPrice(),
                            candidate.changeBasisPoints(),
                            candidate.idempotencyKey()
                    )
            );
            transactionTemplate.executeWithoutResult(status -> markNotified(candidate.alertId(), notificationId));
        } catch (StatusRuntimeException e) {
            log.warn("Wishlist price alert notification request failed: {}", e.getStatus().getCode());
        } catch (RuntimeException e) {
            log.error("Wishlist price alert notification request failed", e);
        }
    }

    private void markNotified(UUID alertId, UUID notificationId) {
        alertReader.findAlert(alertId)
                .ifPresent(alert -> alert.markNotified(notificationId, clock.instant()));
    }

    private PriceAlertCandidate toCandidate(WishlistPriceAlert alert) {
        return new PriceAlertCandidate(
                alert.getId(),
                alert.getUserId(),
                alert.getSymbol(),
                alert.getTradingDate(),
                alert.getDirection(),
                alert.getThresholdBasisPoints(),
                alert.getOpenPrice(),
                alert.getTriggerPrice(),
                alert.getChangeBasisPoints(),
                alert.getIdempotencyKey()
        );
    }

    private static String idempotencyKey(
            UUID userId,
            String symbol,
            java.time.LocalDate tradingDate,
            AlertDirection direction,
            int thresholdBasisPoints
    ) {
        return "wishlist-price-alert:%s:%s:%s:%s:%d"
                .formatted(userId, symbol, tradingDate, direction.name(), thresholdBasisPoints);
    }
}
