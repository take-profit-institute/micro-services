package org.profit.candle.user.profile.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumed_events")
public class ConsumedEvent {

    @Id
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "consumed_at", nullable = false, insertable = false, updatable = false)
    private Instant consumedAt;

    protected ConsumedEvent() {}

    public ConsumedEvent(UUID eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    public UUID eventId() { return eventId; }
    public String eventType() { return eventType; }
}
