package org.profit.candle.learning.content.repository;

import org.profit.candle.learning.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID>, JpaSpecificationExecutor<Content> {

    // 조회수 원자적 증가 — 동시 요청 시 lost update 방지
    @Modifying
    @Query("UPDATE Content c SET c.readCount = c.readCount + 1, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void incrementReadCount(@Param("id") UUID id);

    // 전체 공개 콘텐츠 수 (대시보드용)
    long countByPublishedTrue();

    // 카테고리별 공개 콘텐츠 수
    long countByCategoryAndPublishedTrue(String category);
}
