package org.profit.candle.notification.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events", schema = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OutboxEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, columnDefinition = "text")
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, columnDefinition = "text")
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "idempotency_key", columnDefinition = "text")
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "trace_id", columnDefinition = "text")
    private String traceId;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static OutboxEvent create(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            String payload,
            String idempotencyKey,
            String traceId
    ) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.payload = payload;
        event.idempotencyKey = idempotencyKey;
        event.occurredAt = Instant.now();
        event.traceId = traceId;
        return event;
    }
}
