package org.profit.candle.news.mapping.repository;

import org.profit.candle.news.mapping.entity.ArticleStockMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ArticleStockMappingJpaRepository extends JpaRepository<ArticleStockMapping, UUID> {
    boolean existsByArticleIdAndStockCode(UUID articleId, String stockCode);

    @Modifying
    @Query(value = """
            INSERT INTO news.article_stock_mappings (
                article_id,
                stock_code,
                matched_keyword
            )
            VALUES (
                :articleId,
                :stockCode,
                :matchedKeyword
            )
            ON CONFLICT (article_id, stock_code) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(
            @Param("articleId") UUID articleId,
            @Param("stockCode") String stockCode,
            @Param("matchedKeyword") String matchedKeyword
    );
}
