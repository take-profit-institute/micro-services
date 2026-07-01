package org.profit.candle.stock.catalog.dto;

import org.profit.candle.stock.catalog.entity.StockEntity;

import java.time.Instant;
import java.time.LocalDate;

/** 종목 마스터 조회 결과. */
public record StockResult(
        String code,
        String name,
        String marketType,
        String sector,
        Long marketCap,
        Long sharesOutstanding,
        LocalDate listedAt,
        String listingStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static StockResult from(StockEntity e) {
        return new StockResult(
                e.stockCode(), e.stockName(), e.marketType(), e.sector(),
                e.marketCap(), e.sharesOutstanding(), e.listedAt(), e.listingStatus(),
                e.createdAt(), e.updatedAt());
    }
}
