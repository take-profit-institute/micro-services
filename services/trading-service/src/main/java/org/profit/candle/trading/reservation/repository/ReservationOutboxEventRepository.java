package org.profit.candle.trading.reservation.repository;

import org.profit.candle.trading.reservation.event.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationOutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}
