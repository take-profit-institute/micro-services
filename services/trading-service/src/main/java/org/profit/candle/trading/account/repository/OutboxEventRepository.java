package org.profit.candle.trading.account.repository;

import org.profit.candle.trading.account.event.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}