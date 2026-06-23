package org.profit.candle.user.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IdempotencyRecordId implements Serializable {

    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    @Column(name = "operation", nullable = false, length = 200)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    protected IdempotencyRecordId() {}

    public IdempotencyRecordId(String actorId, String operation, String idempotencyKey) {
        this.actorId = actorId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
    }

    public String actorId() { return actorId; }
    public String operation() { return operation; }
    public String idempotencyKey() { return idempotencyKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyRecordId other)) return false;
        return Objects.equals(actorId, other.actorId)
                && Objects.equals(operation, other.operation)
                && Objects.equals(idempotencyKey, other.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorId, operation, idempotencyKey);
    }
}
