package org.profit.candle.news.target.repository;

import org.profit.candle.news.target.entity.CollectionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CollectionTargetJpaRepository extends JpaRepository<CollectionTarget, UUID> {
    List<CollectionTarget> findByActiveTrueOrderByPriorityAsc();
}
