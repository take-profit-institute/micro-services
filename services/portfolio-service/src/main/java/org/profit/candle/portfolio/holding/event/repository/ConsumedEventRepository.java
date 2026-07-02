package org.profit.candle.portfolio.holding.event.repository;

import org.profit.candle.portfolio.holding.event.entity.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {
}
