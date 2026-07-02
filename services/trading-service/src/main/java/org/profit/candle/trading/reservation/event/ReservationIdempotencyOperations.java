package org.profit.candle.trading.reservation.event;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.reservation.repository.ReservationIdempotencyRecordRepository;
import org.profit.candle.trading.support.idempotency.IdempotencyOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReservationIdempotencyOperations
        implements IdempotencyOperations<ReservationIdempotencyRecordId, IdempotencyRecord> {

    private final ReservationIdempotencyRecordRepository repository;

    @Override
    public ReservationIdempotencyRecordId newId(String actorId, String operation, String idempotencyKey) {
        return new ReservationIdempotencyRecordId(actorId, operation, idempotencyKey);
    }

    @Override
    public Optional<IdempotencyRecord> findById(ReservationIdempotencyRecordId id) {
        return repository.findById(id);
    }

    @Override
    public void save(IdempotencyRecord record) {
        repository.saveAndFlush(record);
    }

    @Override
    public IdempotencyRecord newRecord(ReservationIdempotencyRecordId id, String requestHash,
                                       byte[] responsePayload, String responseType, String grpcCode,
                                       Instant expiresAt) {
        return new IdempotencyRecord(id, requestHash, responsePayload, responseType, grpcCode, expiresAt);
    }

    @Override
    public String requestHash(IdempotencyRecord record) {
        return record.requestHash();
    }

    @Override
    public byte[] responsePayload(IdempotencyRecord record) {
        return record.responsePayload();
    }
}
