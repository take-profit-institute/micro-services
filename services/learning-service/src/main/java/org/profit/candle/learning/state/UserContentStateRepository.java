package org.profit.candle.learning.state;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserContentStateRepository extends JpaRepository<UserContentState, UUID> {

    Optional<UserContentState> findByUserIdAndContentId(UUID userId, UUID contentId);

    // 사용자의 모든 학습 상태 (content JOIN)
    List<UserContentState> findByUserId(UUID userId);

    // 즐겨찾기 목록
    @Query("""
        SELECT s FROM UserContentState s
        JOIN FETCH s.content c
        WHERE s.userId = :userId
        AND s.favorite = true
        AND c.deletedAt IS NULL
        AND c.published = true
    """)
    Page<UserContentState> findFavorites(@Param("userId") UUID userId, Pageable pageable);

    // 사용자 완료 콘텐츠 수
    long countByUserIdAndCompletedTrue(UUID userId);

    // 카테고리별 완료 수 (대시보드용)
    @Query("""
        SELECT s.content.category, COUNT(s)
        FROM UserContentState s
        WHERE s.userId = :userId
        AND s.completed = true
        AND s.content.deletedAt IS NULL
        GROUP BY s.content.category
    """)
    List<Object[]> countCompletedByCategory(@Param("userId") UUID userId);

    // 사용자가 아직 안 본 콘텐츠 ID 목록 (추천용)
    @Query("""
        SELECT c.id FROM Content c
        WHERE c.published = true
        AND c.deletedAt IS NULL
        AND c.id NOT IN (
            SELECT s.content.id FROM UserContentState s WHERE s.userId = :userId
        )
    """)
    List<UUID> findUnreadContentIds(@Param("userId") UUID userId);
}