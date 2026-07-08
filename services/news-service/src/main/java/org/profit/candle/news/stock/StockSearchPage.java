package org.profit.candle.news.stock;

import java.util.List;

public record StockSearchPage(
        List<StockSnapshot> stocks,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
