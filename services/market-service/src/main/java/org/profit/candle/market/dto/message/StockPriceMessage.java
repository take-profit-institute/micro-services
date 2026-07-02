package org.profit.candle.market.dto.message;

public record StockPriceMessage(
        String stockCode,
        String stockName,
        int currentPrice,
        int priceChange,
        double priceChangeRate,
        long tradingVolume
) {
}
