package org.profit.candle.trading.account.repository;

import java.util.Optional;
import org.profit.candle.trading.account.event.IdempotencyRecord;
import org.profit.candle.trading.account.event.IdempotencyRecordId;

/** Account 스키마의 idempotency_records 영속화만 담당한다. */
public interface IdempotencyRecordRepository {

    Optional<IdempotencyRecord> findById(IdempotencyRecordId id);

    IdempotencyRecord saveAndFlush(IdempotencyRecord record);
}