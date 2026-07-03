package org.profit.candle.news.article.dto;

import org.profit.candle.news.article.repository.ArticleJpaRepository.StockNewsArticleProjection;

import java.time.Instant;
import java.util.UUID;

public record StockNewsArticleResult(
        UUID id,
        String title,
        String contentSummary,
        String url,
        String source,
        Instant publishedAt,
        Instant createdAt
) {
    public static StockNewsArticleResult from(StockNewsArticleProjection projection) {
        return new StockNewsArticleResult(
                projection.getId(),
                projection.getTitle(),
                projection.getContentSummary(),
                projection.getUrl(),
                projection.getSource(),
                projection.getPublishedAt(),
                projection.getCreatedAt()
        );
    }
}
