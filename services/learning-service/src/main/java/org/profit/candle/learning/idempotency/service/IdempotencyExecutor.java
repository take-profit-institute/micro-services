package org.profit.candle.learning.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.exception.LearningErrorCode;
import org.profit.candle.learning.exception.LearningException;
import org.profit.candle.learning.idempotency.entity.IdempotencyRecord;
import org.profit.candle.learning.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyExecutor {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> T execute(UUID userId, String operation, String idempotencyKey,
                         String requestHash, Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new LearningException(LearningErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        return idempotencyRecordRepository
                .findByUserIdAndOperationAndIdempotencyKey(userId, operation, idempotencyKey)
                .map(record -> restore(record, requestHash, responseType))
                .orElseGet(() -> executeAndStore(userId, operation, idempotencyKey,
                        requestHash, responseType, action));
    }

    private <T> T executeAndStore(UUID userId, String operation, String idempotencyKey,
                                  String requestHash, Class<T> responseType, Supplier<T> action) {
        T response = action.get();
        idempotencyRecordRepository.save(IdempotencyRecord.create(
                userId, operation, idempotencyKey, requestHash,
                toJson(response), responseType.getName()));
        return response;
    }

    private <T> T restore(IdempotencyRecord record, String requestHash, Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new LearningException(LearningErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }
        try {
            return objectMapper.readValue(record.getResponseJson(), responseType);
        } catch (Exception e) {
            throw new LearningException(LearningErrorCode.INTERNAL_ERROR);
        }
    }

    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new LearningException(LearningErrorCode.INTERNAL_ERROR);
        }
    }
}