package org.profit.candle.news.stock;

public interface StockClient {
    StockSnapshot getStock(String code);

    StockSearchPage listListedStocks(int page, int size);
}
