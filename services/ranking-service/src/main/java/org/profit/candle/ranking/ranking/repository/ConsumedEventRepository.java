package org.profit.candle.ranking.ranking.repository;

import org.profit.candle.ranking.ranking.entity.ConsumedEvent;
import org.profit.candle.ranking.ranking.entity.ConsumedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, ConsumedEventId> {}
