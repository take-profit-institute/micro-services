package org.profit.candle.trading.idempotency.repository;

import org.profit.candle.trading.idempotency.entity.IdempotencyRecord;
import org.profit.candle.trading.idempotency.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 멱등성 레코드 조회·unique insert만 담당한다 (스펙 §6).
 * 업무 판단·hash 계산은 하지 않는다.
 */
public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {
}
