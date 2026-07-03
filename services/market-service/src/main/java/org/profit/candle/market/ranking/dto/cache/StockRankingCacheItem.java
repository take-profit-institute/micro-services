package org.profit.candle.market.ranking.dto.cache;

public record StockRankingCacheItem(
        int rank,
        String stockCode,
        String stockName,
        long currentPrice,
        long priceChange,
        double priceChangeRate,
        String priceChangeSign,
        long tradingVolume
) {
}
