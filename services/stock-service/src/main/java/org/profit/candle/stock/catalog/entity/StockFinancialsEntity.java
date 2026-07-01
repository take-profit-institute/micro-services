package org.profit.candle.stock.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;

/** 종목 재무지표(분기 스냅샷). */
@Entity
@Table(name = "stock_financials")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockFinancialsEntity {

    @EmbeddedId
    private StockFinancialsId id;

    private Long revenue;

    @Column(name = "operating_profit")
    private Long operatingProfit;

    @Column(name = "net_income")
    private Long netIncome;

    @Column(precision = 10, scale = 2)
    private BigDecimal per;

    @Column(precision = 10, scale = 2)
    private BigDecimal pbr;

    @Column(precision = 6, scale = 2)
    private BigDecimal roe;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
