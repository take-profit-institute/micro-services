package org.profit.candle.trading.account.repository;

import org.profit.candle.trading.account.event.IdempotencyRecord;
import org.profit.candle.trading.account.event.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAccountIdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId>, AccountIdempotencyRecordRepository {
}