package org.profit.candle.notification.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.profit.candle.notification.idempotency.entity.IdempotencyRecord;
import org.profit.candle.notification.idempotency.repository.IdempotencyRecordJpaRepository;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyExecutor {

    private final IdempotencyRecordJpaRepository idempotencyRecordJpaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> T execute(
            UUID userId,
            String operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType,
            Supplier<T> action
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new NotificationException(NotificationErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        return idempotencyRecordJpaRepository
                .findByUserIdAndOperationAndIdempotencyKey(userId, operation, idempotencyKey)
                .map(record -> restore(record, requestHash, responseType))
                .orElseGet(() -> executeAndStore(
                        userId,
                        operation,
                        idempotencyKey,
                        requestHash,
                        responseType,
                        action
                ));
    }

    private <T> T executeAndStore(
            UUID userId,
            String operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType,
            Supplier<T> action
    ) {
        T response = action.get();
        idempotencyRecordJpaRepository.save(IdempotencyRecord.create(
                userId,
                operation,
                idempotencyKey,
                requestHash,
                toJson(response),
                responseType.getName()
        ));
        return response;
    }

    private <T> T restore(
            IdempotencyRecord record,
            String requestHash,
            Class<T> responseType
    ) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new NotificationException(NotificationErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }

        try {
            return objectMapper.readValue(record.getResponseJson(), responseType);
        } catch (JsonProcessingException e) {
            throw new NotificationException(NotificationErrorCode.INVALID_REQUEST, e);
        }
    }

    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new NotificationException(NotificationErrorCode.INVALID_REQUEST, e);
        }
    }
}
