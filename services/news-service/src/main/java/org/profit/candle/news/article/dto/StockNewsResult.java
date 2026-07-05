package org.profit.candle.news.article.dto;

import java.util.List;

public record StockNewsResult(
        List<StockNewsArticleResult> articles
) {
}
