package org.profit.candle.notification.idempotency.repository;

import java.util.Optional;
import java.util.UUID;
import org.profit.candle.notification.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordJpaRepository
        extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(
            UUID userId,
            String operation,
            String idempotencyKey
    );
}
