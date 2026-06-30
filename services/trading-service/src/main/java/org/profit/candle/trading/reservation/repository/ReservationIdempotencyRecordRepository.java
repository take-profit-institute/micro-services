package org.profit.candle.trading.reservation.repository;

import org.profit.candle.trading.reservation.event.IdempotencyRecord;
import org.profit.candle.trading.reservation.event.ReservationIdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationIdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, ReservationIdempotencyRecordId> {}
