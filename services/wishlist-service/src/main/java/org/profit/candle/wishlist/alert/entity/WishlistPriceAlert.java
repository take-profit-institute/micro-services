package org.profit.candle.wishlist.alert.entity;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wishlist_price_alerts")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class WishlistPriceAlert {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertDirection direction;

    @Column(name = "threshold_basis_points", nullable = false)
    private int thresholdBasisPoints;

    @Column(name = "open_price", nullable = false)
    private long openPrice;

    @Column(name = "trigger_price", nullable = false)
    private long triggerPrice;

    @Column(name = "change_basis_points", nullable = false)
    private int changeBasisPoints;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private WishlistPriceAlert(
            UUID userId,
            String symbol,
            LocalDate tradingDate,
            AlertDirection direction,
            int thresholdBasisPoints,
            long openPrice,
            long triggerPrice,
            int changeBasisPoints,
            String idempotencyKey,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.symbol = symbol;
        this.tradingDate = tradingDate;
        this.direction = direction;
        this.thresholdBasisPoints = thresholdBasisPoints;
        this.openPrice = openPrice;
        this.triggerPrice = triggerPrice;
        this.changeBasisPoints = changeBasisPoints;
        this.idempotencyKey = idempotencyKey;
        this.triggeredAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static WishlistPriceAlert trigger(
            UUID userId,
            String symbol,
            LocalDate tradingDate,
            AlertDirection direction,
            int thresholdBasisPoints,
            long openPrice,
            long triggerPrice,
            int changeBasisPoints,
            String idempotencyKey,
            Instant now
    ) {
        return new WishlistPriceAlert(
                userId,
                symbol,
                tradingDate,
                direction,
                thresholdBasisPoints,
                openPrice,
                triggerPrice,
                changeBasisPoints,
                idempotencyKey,
                now
        );
    }

    public void markNotified(UUID notificationId, Instant now) {
        this.notificationId = notificationId;
        this.updatedAt = now;
    }
}
