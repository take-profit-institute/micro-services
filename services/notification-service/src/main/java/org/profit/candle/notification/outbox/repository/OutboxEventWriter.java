package org.profit.candle.notification.outbox.repository;

import org.profit.candle.notification.outbox.entity.OutboxEvent;

public interface OutboxEventWriter {

    OutboxEvent save(OutboxEvent event);
}
