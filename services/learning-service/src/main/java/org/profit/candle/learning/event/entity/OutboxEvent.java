package org.profit.candle.learning.event.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "learning")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String eventType;

    @Column(nullable = false, length = 120)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(UUID id, String eventType, String aggregateId,
                       String payload, Instant occurredAt) {
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