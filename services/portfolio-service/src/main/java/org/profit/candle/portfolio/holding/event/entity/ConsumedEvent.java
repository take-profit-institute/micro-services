package org.profit.candle.portfolio.holding.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolio_consumed_events")
public class ConsumedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedEvent() {}

    public ConsumedEvent(UUID eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.consumedAt = Instant.now();
    }

    public UUID eventId() { return eventId; }
    public String eventType() { return eventType; }
    public Instant consumedAt() { return consumedAt; }
}
