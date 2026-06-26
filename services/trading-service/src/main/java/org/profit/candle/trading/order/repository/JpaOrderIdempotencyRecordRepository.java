package org.profit.candle.trading.order.repository;

import org.profit.candle.trading.order.event.IdempotencyRecord;
import org.profit.candle.trading.order.event.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaOrderIdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId>, OrderIdempotencyRecordRepository {
}