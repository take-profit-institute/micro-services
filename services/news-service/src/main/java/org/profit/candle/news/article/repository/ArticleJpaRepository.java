package org.profit.candle.news.article.repository;

import org.profit.candle.news.article.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleJpaRepository extends JpaRepository<Article, UUID> {
    Optional<Article> findByUrl(String url);

    @Modifying
    @Query(value = """
            INSERT INTO news.articles (
                title,
                content_summary,
                url,
                source,
                published_at
            )
            VALUES (
                :title,
                :contentSummary,
                :url,
                :source,
                :publishedAt
            )
            ON CONFLICT (url) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(
            @Param("title") String title,
            @Param("contentSummary") String contentSummary,
            @Param("url") String url,
            @Param("source") String source,
            @Param("publishedAt") Instant publishedAt
    );

    @Query(value = """
            SELECT
                a.id AS id,
                a.title AS title,
                a.content_summary AS contentSummary,
                a.url AS url,
                a.source AS source,
                a.published_at AS publishedAt,
                a.created_at AS createdAt
            FROM news.articles a
            INNER JOIN news.article_stock_mappings m
                ON m.article_id = a.id
            WHERE m.stock_code = :stockCode
            ORDER BY a.published_at DESC NULLS LAST, a.created_at DESC
            LIMIT 3
            """, nativeQuery = true)
    List<StockNewsArticleProjection> findTop3ByStockCode(@Param("stockCode") String stockCode);

    interface StockNewsArticleProjection {
        UUID getId();

        String getTitle();

        String getContentSummary();

        String getUrl();

        String getSource();

        Instant getPublishedAt();

        Instant getCreatedAt();
    }
}
