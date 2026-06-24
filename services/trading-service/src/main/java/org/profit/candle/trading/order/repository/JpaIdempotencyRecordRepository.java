package org.profit.candle.trading.order.repository;

import org.profit.candle.trading.order.event.IdempotencyRecord;
import org.profit.candle.trading.order.event.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaIdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId>, IdempotencyRecordRepository {
}