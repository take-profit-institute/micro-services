package org.profit.candle.trading.order.repository;

import org.profit.candle.trading.order.event.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderOutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}