package org.profit.candle.stock.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Embeddable
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockFinancialsId implements Serializable {

    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "fiscal_period", length = 7)
    private String fiscalPeriod;

    public StockFinancialsId(Long stockId, String fiscalPeriod) {
        this.stockId = stockId;
        this.fiscalPeriod = fiscalPeriod;
    }
}
