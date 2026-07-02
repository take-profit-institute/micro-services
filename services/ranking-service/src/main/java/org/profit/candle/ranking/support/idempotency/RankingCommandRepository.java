package org.profit.candle.ranking.support.idempotency;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RankingCommandRepository {

    /** 같은 scope로 저장된 성공 응답을 조회한다. */
    Optional<IdempotencyRecord> findRecord(String actorId, String operation, String idempotencyKey);

    /** 성공 응답을 TTL과 함께 저장한다. */
    void saveRecord(IdempotencyRecord record);

    /** 일별 랭킹 완료 이벤트를 Outbox에 저장한다. */
    void saveOutbox(OutboxEvent event);

    /** 아직 발행되지 않은 Outbox 이벤트를 오래된 순서로 조회한다. */
    List<OutboxEvent> findPendingOutbox(int limit);

    /** Kafka 발행이 끝난 Outbox 이벤트를 완료 처리한다. */
    void markOutboxPublished(UUID eventId, Instant publishedAt);

    /** TTL이 지난 멱등성 성공 응답을 삭제한다. */
    int deleteExpiredRecords(Instant now);

    record IdempotencyRecord(
            String actorId,
            String operation,
            String idempotencyKey,
            String requestHash,
            byte[] responsePayload,
            String responseType,
            Instant expiresAt) {}

    record OutboxEvent(
            UUID eventId,
            String eventType,
            String aggregateId,
            String payload,
            Instant occurredAt) {}
}
