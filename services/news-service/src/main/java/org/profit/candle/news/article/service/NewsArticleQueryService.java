package org.profit.candle.news.article.service;

import org.profit.candle.news.article.dto.StockNewsResult;

public interface NewsArticleQueryService {
    StockNewsResult getStockNews(String stockCode);
}
