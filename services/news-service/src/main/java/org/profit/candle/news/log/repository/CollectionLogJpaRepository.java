package org.profit.candle.news.log.repository;

import org.profit.candle.news.log.entity.CollectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CollectionLogJpaRepository extends JpaRepository<CollectionLog, UUID> {
}
