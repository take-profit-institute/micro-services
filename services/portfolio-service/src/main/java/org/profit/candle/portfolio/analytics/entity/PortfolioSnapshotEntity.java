package org.profit.candle.portfolio.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "portfolio_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "snapshot_date"})
)
public class PortfolioSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_asset", nullable = false)
    private long totalAsset;

    @Column(name = "stock_value", nullable = false)
    private long stockValue;

    @Column(name = "daily_profit", nullable = false)
    private long dailyProfit;

    @Column(name = "cumulative_return_rate", length = 20, nullable = false)
    private String cumulativeReturnRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PortfolioSnapshotEntity() {}

    public PortfolioSnapshotEntity(String userId, LocalDate snapshotDate, long totalAsset,
                                   long stockValue, long dailyProfit, String cumulativeReturnRate) {
        this.userId = userId;
        this.snapshotDate = snapshotDate;
        this.totalAsset = totalAsset;
        this.stockValue = stockValue;
        this.dailyProfit = dailyProfit;
        this.cumulativeReturnRate = cumulativeReturnRate;
        this.createdAt = Instant.now();
    }

    public Long id() { return id; }
    public String userId() { return userId; }
    public LocalDate snapshotDate() { return snapshotDate; }
    public long totalAsset() { return totalAsset; }
    public long stockValue() { return stockValue; }
    public long dailyProfit() { return dailyProfit; }
    public String cumulativeReturnRate() { return cumulativeReturnRate; }
    public Instant createdAt() { return createdAt; }
}
