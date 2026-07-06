package org.profit.candle.learning.idempotency.repository;

import org.profit.candle.learning.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(
            UUID userId, String operation, String idempotencyKey);
}