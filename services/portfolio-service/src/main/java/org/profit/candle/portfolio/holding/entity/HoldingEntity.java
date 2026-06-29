package org.profit.candle.portfolio.holding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "portfolio_holdings")
public class HoldingEntity {

    @EmbeddedId
    private HoldingId id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "average_price", nullable = false)
    private long averagePrice;

    @Column(name = "book_value", nullable = false)
    private long bookValue;

    // 마지막으로 알려진 현재가 (시세 업데이트 or 체결가 기준). 근사값.
    @Column(name = "cached_current_price", nullable = false)
    private long cachedCurrentPrice;

    @Column(name = "realized_profit", nullable = false)
    private long realizedProfit;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 100, nullable = false)
    private String sector;

    @Column(length = 20, nullable = false)
    private String market;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HoldingEntity() {}

    public HoldingEntity(String userId, String symbol, String name, String sector, String market) {
        this.id = new HoldingId(userId, symbol);
        this.name = name != null ? name : "";
        this.sector = sector != null ? sector : "";
        this.market = market != null ? market : "";
        this.quantity = 0;
        this.averagePrice = 0;
        this.bookValue = 0;
        this.cachedCurrentPrice = 0;
        this.realizedProfit = 0;
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public HoldingId id() { return id; }
    public String userId() { return id.userId(); }
    public String symbol() { return id.symbol(); }
    public String name() { return name; }
    public long quantity() { return quantity; }
    public long averagePrice() { return averagePrice; }
    public long bookValue() { return bookValue; }
    public long cachedCurrentPrice() { return cachedCurrentPrice; }
    public long realizedProfit() { return realizedProfit; }
    public boolean active() { return active; }
    public String sector() { return sector; }
    public String market() { return market; }
    public Instant updatedAt() { return updatedAt; }

    public void applyBuy(long quantity, long price) {
        long newQuantity = this.quantity + quantity;
        this.averagePrice = (this.averagePrice * this.quantity + price * quantity) / newQuantity;
        this.quantity = newQuantity;
        this.bookValue = this.quantity * this.averagePrice;
        this.cachedCurrentPrice = price;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void applySell(long quantity, long price) {
        long profit = (price - this.averagePrice) * quantity;
        this.realizedProfit += profit;
        this.quantity -= quantity;
        this.bookValue = this.quantity * this.averagePrice;
        this.cachedCurrentPrice = price;
        this.active = this.quantity > 0;
        this.updatedAt = Instant.now();
    }

    public void updateCachedPrice(long currentPrice) {
        this.cachedCurrentPrice = currentPrice;
        this.updatedAt = Instant.now();
    }
}
