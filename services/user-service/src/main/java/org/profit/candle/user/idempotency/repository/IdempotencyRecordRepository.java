package org.profit.candle.user.idempotency.repository;

import org.profit.candle.user.idempotency.entity.IdempotencyRecord;
import org.profit.candle.user.idempotency.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {}
