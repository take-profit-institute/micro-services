package org.profit.candle.stock.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;

/** 종목 마스터(catalog). SEED/배치/키움이 upsert 한다. */
@Entity
@Table(name = "stocks")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "stock_code", nullable = false, unique = true, length = 6)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(name = "market_type", nullable = false, length = 20)
    private String marketType;

    @Column(length = 50)
    private String sector;

    @Column(name = "listing_status", nullable = false, length = 20)
    private String listingStatus;

    @Column(name = "market_cap")
    private Long marketCap;

    @Column(name = "shares_outstanding")
    private Long sharesOutstanding;

    @Column(name = "listed_at")
    private LocalDate listedAt;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public StockEntity(String stockCode, String stockName, String marketType) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.marketType = marketType;
        this.listingStatus = "LISTED";
        this.dataSource = "SEED";
    }

    /** 키움/배치 기준정보로 갱신. null 인 항목은 기존 값을 유지한다. */
    public void applyReferenceData(String stockName, String marketType, String sector, Long marketCap,
            Long sharesOutstanding, LocalDate listedAt, String listingStatus, String dataSource) {
        if (stockName != null) this.stockName = stockName;
        if (marketType != null) this.marketType = marketType;
        if (sector != null) this.sector = sector;
        if (marketCap != null) this.marketCap = marketCap;
        if (sharesOutstanding != null) this.sharesOutstanding = sharesOutstanding;
        if (listedAt != null) this.listedAt = listedAt;
        if (listingStatus != null) this.listingStatus = listingStatus;
        this.dataSource = dataSource;
        this.syncedAt = Instant.now();
    }
}
