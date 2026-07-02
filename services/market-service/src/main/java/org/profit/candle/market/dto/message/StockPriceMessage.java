package org.profit.candle.market.dto.message;

public record StockPriceMessage(
        String stockCode,
        String stockName,
        int currentPrice,
        int priceChange,
        double priceChangeRate,
        long tradingVolume,
        String timestamp   // ISO-8601 (발행 시각). 프론트 실시간 그래프의 x축.
) {
}
