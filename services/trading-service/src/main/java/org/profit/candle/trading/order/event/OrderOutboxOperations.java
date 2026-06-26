package org.profit.candle.trading.order.event;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.support.event.OutboxOperations;
import org.profit.candle.trading.order.repository.OrderOutboxEventRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderOutboxOperations implements OutboxOperations<OutboxEvent> {

    private final OrderOutboxEventRepository repository;

    @Override
    public OutboxEvent newEvent(UUID id, String eventType, String aggregateId, String payload, Instant occurredAt) {
        return new OutboxEvent(id, eventType, aggregateId, payload, occurredAt);
    }

    @Override
    public void save(OutboxEvent event) {
        repository.save(event);
    }
}