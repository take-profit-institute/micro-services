package org.profit.candle.learning.content;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID> {

    // 조회수 원자적 증가 — 동시 요청 시 lost update 방지
    @Modifying
    @Query("UPDATE Content c SET c.readCount = c.readCount + 1, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void incrementReadCount(@Param("id") UUID id);

    // 공개 콘텐츠 목록 (카테고리 + 레벨 필터)
    @Query("""
        SELECT c FROM Content c
        WHERE c.published = true
        AND (:category IS NULL OR c.category = :category)
        AND (:level IS NULL OR c.level = :level)
    """)
    Page<Content> findPublished(@Param("category") String category,
                                @Param("level") ContentLevel level,
                                Pageable pageable);

    // 제목 검색 (LIKE)
    @Query("""
        SELECT c FROM Content c
        WHERE c.published = true
        AND (:category IS NULL OR c.category = :category)
        AND (:level IS NULL OR c.level = :level)
        AND LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<Content> searchByTitle(@Param("query") String query,
                                @Param("category") String category,
                                @Param("level") ContentLevel level,
                                Pageable pageable);

    // 키워드 검색 (PostgreSQL array overlap)
    @Query(value = """
        SELECT * FROM learning.contents c
        WHERE c.deleted_at IS NULL
        AND c.is_published = true
        AND (:category IS NULL OR c.category = :category)
        AND (:level IS NULL OR c.level = CAST(:level AS learning.content_level_type))
        AND c.keywords && CAST(:keywords AS text[])
    """, nativeQuery = true)
    Page<Content> searchByKeywords(@Param("keywords") String[] keywords,
                                   @Param("category") String category,
                                   @Param("level") String level,
                                   Pageable pageable);

    // 전체 공개 콘텐츠 수 (대시보드용)
    long countByPublishedTrue();

    // 카테고리별 공개 콘텐츠 수
    long countByCategoryAndPublishedTrue(String category);
}