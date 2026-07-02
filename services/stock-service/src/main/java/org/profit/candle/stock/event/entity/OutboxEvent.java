package org.profit.candle.stock.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String eventType;
    @Column(nullable = false, length = 120) private String aggregateId;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Column(nullable = false) private Instant occurredAt;
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
    public void markPublished(Instant now) { publishedAt = now; }
}
