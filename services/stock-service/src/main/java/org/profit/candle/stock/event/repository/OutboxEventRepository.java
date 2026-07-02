package org.profit.candle.stock.event.repository;

import java.util.List;
import java.util.UUID;
import org.profit.candle.stock.event.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}
