package org.profit.candle.news.mapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "article_stock_mappings", schema = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleStockMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "matched_keyword", columnDefinition = "text")
    private String matchedKeyword;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    private ArticleStockMapping(UUID articleId, String stockCode, String matchedKeyword) {
        this.articleId = articleId;
        this.stockCode = stockCode;
        this.matchedKeyword = matchedKeyword;
    }

    public static ArticleStockMapping create(UUID articleId, String stockCode, String matchedKeyword) {
        return new ArticleStockMapping(articleId, stockCode, matchedKeyword);
    }
}
