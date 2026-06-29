package org.profit.candle.user.profile.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEvent() {}

    public OutboxEvent(String topic, String partitionKey, String payload) {
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.published = false;
        this.createdAt = Instant.now();
    }

    public UUID id() { return id; }
    public String topic() { return topic; }
    public String partitionKey() { return partitionKey; }
    public String payload() { return payload; }
    public boolean published() { return published; }
    public Instant createdAt() { return createdAt; }

    public void markPublished() {
        this.published = true;
    }
}
