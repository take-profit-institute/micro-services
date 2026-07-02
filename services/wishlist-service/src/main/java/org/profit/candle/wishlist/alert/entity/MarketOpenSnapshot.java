package org.profit.candle.wishlist.alert.entity;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_open_snapshots")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class MarketOpenSnapshot {
    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(name = "open_price", nullable = false)
    private long openPrice;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_price")
    private Long lastPrice;

    @Column(name = "last_change_basis_points")
    private Integer lastChangeBasisPoints;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private MarketOpenSnapshot(String symbol, LocalDate tradingDate, long openPrice, Instant now) {
        this.id = UUID.randomUUID();
        this.symbol = symbol;
        this.tradingDate = tradingDate;
        this.openPrice = openPrice;
        this.firstSeenAt = now;
        this.updatedAt = now;
    }

    public static MarketOpenSnapshot open(String symbol, LocalDate tradingDate, long openPrice, Instant now) {
        return new MarketOpenSnapshot(symbol, tradingDate, openPrice, now);
    }

    public void observe(long price, int changeBasisPoints, Instant now) {
        this.lastPrice = price;
        this.lastChangeBasisPoints = changeBasisPoints;
        this.updatedAt = now;
    }
}
