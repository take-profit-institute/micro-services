package org.profit.candle.stock.catalog.dto;

/** 상세 조회 결과. financials 는 아직 없을 수 있다(키움 fallback 직후 등). */
public record StockDetailResult(StockResult stock, StockFinancialsResult financials, StockDataSource source) {
}
