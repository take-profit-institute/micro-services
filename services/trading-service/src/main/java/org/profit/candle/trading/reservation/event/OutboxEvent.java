package org.profit.candle.trading.reservation.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * reservation 스키마 전용 outbox 테이블 — SQL 컨벤션 9장, 1장 스키마 목록.
 * 시가+지정가 케이스의 ReservationDue 이벤트가 이 테이블을 통해 발행되어
 * order_svc로 전달된다 (Option C: 전체 비동기 Kafka 전환).
 */
@Entity(name = "ReservationOutboxEvent")
@Table(name = "outbox_events", schema = "reservation")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(UUID id, String eventType, String aggregateId, String payload, Instant occurredAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public UUID id() { return id; }
    public String eventType() { return eventType; }
    public String aggregateId() { return aggregateId; }
    public String payload() { return payload; }
    public void markPublished(Instant now) { this.publishedAt = now; }
}
