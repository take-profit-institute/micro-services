package org.profit.candle.wishlist.event.repository;

import java.util.List;
import java.util.UUID;
import org.profit.candle.wishlist.event.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}
