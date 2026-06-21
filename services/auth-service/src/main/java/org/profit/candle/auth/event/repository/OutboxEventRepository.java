package org.profit.candle.auth.event.repository;

import java.util.List;
import org.profit.candle.auth.event.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}
