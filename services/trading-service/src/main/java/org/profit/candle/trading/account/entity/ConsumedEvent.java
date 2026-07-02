package org.profit.candle.trading.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka 컨슈머 멱등성 보장용 이벤트 처리 기록.
 * event_id(UUID) PK로 같은 이벤트가 두 번 오면 existsById로 스킵한다 —
 * Kafka→Consumer 멱등성은 event_id만으로 충분하다(request_hash 불필요).
 */
@Entity
@Table(schema = "account", name = "consumed_events")
public class ConsumedEvent {

    @Id
    @Column(name = "event_id")
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