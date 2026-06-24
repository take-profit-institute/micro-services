package org.profit.candle.trading.account.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** account 스키마 전용 outbox 테이블 — SQL 컨벤션 9장, 11.2절. */
@Entity
@Table(name = "outbox_events", schema = "account")
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