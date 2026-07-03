package org.profit.candle.news.stock;

public record StockSnapshot(
        String code,
        String name,
        String market,
        String sector,
        long marketCap,
        long sharesOutstanding,
        String status
) {
}
