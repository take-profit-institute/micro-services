package org.profit.candle.ranking.support.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcRankingCommandRepository implements RankingCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 복합 scope로 저장된 멱등성 응답을 조회한다. */
    @Override
    public Optional<IdempotencyRecord> findRecord(String actorId, String operation, String idempotencyKey) {
        return jdbcTemplate.query("""
                        SELECT actor_id, operation, idempotency_key, request_hash,
                               response_payload, response_type, expires_at
                        FROM ranking_idempotency_records
                        WHERE actor_id = ? AND operation = ? AND idempotency_key = ?
                        """, (resultSet, rowNumber) -> new IdempotencyRecord(
                        resultSet.getString("actor_id"),
                        resultSet.getString("operation"),
                        resultSet.getString("idempotency_key"),
                        resultSet.getString("request_hash"),
                        resultSet.getBytes("response_payload"),
                        resultSet.getString("response_type"),
                        resultSet.getTimestamp("expires_at").toInstant()),
                actorId, operation, idempotencyKey).stream().findFirst();
    }

    /** 성공 응답을 저장한다. */
    @Override
    public void saveRecord(IdempotencyRecord record) {
        jdbcTemplate.update("""
                INSERT INTO ranking_idempotency_records
                    (actor_id, operation, idempotency_key, request_hash,
                     response_payload, response_type, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, record.actorId(), record.operation(), record.idempotencyKey(), record.requestHash(),
                record.responsePayload(), record.responseType(), Timestamp.from(record.expiresAt()));
    }

    /** Outbox 이벤트를 저장한다. */
    @Override
    public void saveOutbox(OutboxEvent event) {
        jdbcTemplate.update("""
                INSERT INTO ranking_outbox_events
                    (event_id, event_type, aggregate_id, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """, event.eventId(), event.eventType(), event.aggregateId(), event.payload(),
                Timestamp.from(event.occurredAt()));
    }

    /** 발행 대기 이벤트를 제한된 개수만 조회한다. */
    @Override
    public List<OutboxEvent> findPendingOutbox(int limit) {
        return jdbcTemplate.query("""
                SELECT event_id, event_type, aggregate_id, payload, occurred_at
                FROM ranking_outbox_events
                WHERE published_at IS NULL
                ORDER BY occurred_at
                LIMIT ?
                """, (resultSet, rowNumber) -> new OutboxEvent(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("aggregate_id"),
                resultSet.getString("payload"),
                resultSet.getTimestamp("occurred_at").toInstant()), limit);
    }

    /** 발행 완료 시간을 저장한다. */
    @Override
    public void markOutboxPublished(UUID eventId, Instant publishedAt) {
        jdbcTemplate.update(
                "UPDATE ranking_outbox_events SET published_at = ? WHERE event_id = ?",
                Timestamp.from(publishedAt), eventId);
    }

    /** 만료된 응답을 정리한다. */
    @Override
    public int deleteExpiredRecords(Instant now) {
        return jdbcTemplate.update(
                "DELETE FROM ranking_idempotency_records WHERE expires_at < ?", Timestamp.from(now));
    }
}
