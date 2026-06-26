package org.profit.candle.trading.account.event;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.repository.AccountIdempotencyRecordRepository;
import org.profit.candle.trading.support.idempotency.IdempotencyOperations;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountIdempotencyOperations
        implements IdempotencyOperations<IdempotencyRecordId, IdempotencyRecord> {

    private final AccountIdempotencyRecordRepository repository;

    @Override
    public IdempotencyRecordId newId(String actorId, String operation, String idempotencyKey) {
        return new IdempotencyRecordId(actorId, operation, idempotencyKey);
    }

    @Override
    public Optional<IdempotencyRecord> findById(IdempotencyRecordId id) {
        return repository.findById(id);
    }

    @Override
    public void save(IdempotencyRecord record) {
        repository.saveAndFlush(record);
    }

    @Override
    public IdempotencyRecord newRecord(IdempotencyRecordId id, String requestHash, byte[] responsePayload,
                                       String responseType, String grpcCode, Instant expiresAt) {
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