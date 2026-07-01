package org.profit.candle.trading.order.repository;

import java.util.Optional;
import org.profit.candle.trading.order.event.IdempotencyRecord;
import org.profit.candle.trading.order.event.IdempotencyRecordId;

/** Order 스키마의 idempotency_records 영속화만 담당한다. */
public interface OrderIdempotencyRecordRepository {

    Optional<IdempotencyRecord> findById(IdempotencyRecordId id);

    IdempotencyRecord saveAndFlush(IdempotencyRecord record);
}