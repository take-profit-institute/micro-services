package org.profit.candle.stock.catalog.dto;

import org.profit.candle.stock.catalog.entity.StockFinancialsEntity;

import java.math.BigDecimal;

public record StockFinancialsResult(
        Long revenue,
        Long operatingProfit,
        Long netIncome,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal roe,
        String fiscalPeriod) {

    public static StockFinancialsResult from(StockFinancialsEntity e) {
        return new StockFinancialsResult(
                e.revenue(), e.operatingProfit(), e.netIncome(),
                e.per(), e.pbr(), e.roe(), e.id().fiscalPeriod());
    }
}
