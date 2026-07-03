package org.profit.candle.news.article.entity;

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
@Table(name = "articles", schema = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "content_summary", columnDefinition = "text")
    private String contentSummary;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String url;

    @Column(columnDefinition = "text")
    private String source;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    private Article(
            String title,
            String contentSummary,
            String url,
            String source,
            Instant publishedAt
    ) {
        this.title = title;
        this.contentSummary = contentSummary;
        this.url = url;
        this.source = source;
        this.publishedAt = publishedAt;
    }

    public static Article create(
            String title,
            String contentSummary,
            String url,
            String source,
            Instant publishedAt
    ) {
        return new Article(title, contentSummary, url, source, publishedAt);
    }
}
