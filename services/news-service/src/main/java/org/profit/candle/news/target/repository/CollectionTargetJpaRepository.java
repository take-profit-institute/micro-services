package org.profit.candle.news.target.repository;

import org.profit.candle.news.target.entity.CollectionTarget;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CollectionTargetJpaRepository extends JpaRepository<CollectionTarget, UUID> {
    List<CollectionTarget> findByActiveTrueOrderByPriorityAsc();

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO news.collection_targets (stock_code, target_type, priority, is_active)
            VALUES (:stockCode, CAST('admin' AS news.collection_target_type), :priority, true)
            ON CONFLICT (stock_code, target_type)
            DO UPDATE SET is_active = true
            """, nativeQuery = true)
    int upsertListedAdminTarget(@Param("stockCode") String stockCode, @Param("priority") int priority);
}
