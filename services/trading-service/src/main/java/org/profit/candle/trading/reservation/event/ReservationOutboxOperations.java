package org.profit.candle.trading.reservation.event;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.reservation.repository.ReservationOutboxEventRepository;
import org.profit.candle.trading.support.event.OutboxOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationOutboxOperations implements OutboxOperations<OutboxEvent> {

    private final ReservationOutboxEventRepository repository;

    @Override
    public OutboxEvent newEvent(UUID id, String eventType, String aggregateId, String payload, Instant occurredAt) {
        return new OutboxEvent(id, eventType, aggregateId, payload, occurredAt);
    }

    @Override
    public void save(OutboxEvent event) {
        repository.save(event);
    }
}
