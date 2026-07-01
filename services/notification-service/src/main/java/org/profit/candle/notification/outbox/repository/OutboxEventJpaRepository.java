package org.profit.candle.notification.outbox.repository;

import java.util.UUID;
import org.profit.candle.notification.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, UUID> {
}
