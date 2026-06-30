package org.profit.candle.notification.outbox.repository;

import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.outbox.entity.OutboxEvent;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaOutboxEventWriter implements OutboxEventWriter {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return outboxEventJpaRepository.save(event);
    }
}
