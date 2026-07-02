package org.profit.candle.trading.reservation.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationIdempotencyRecordId implements Serializable {

    @Column(name = "actor_id", nullable = false, length = 120)
    private String actorId;

    @Column(name = "operation", nullable = false, length = 200)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    public ReservationIdempotencyRecordId(String actorId, String operation, String idempotencyKey) {
        this.actorId = actorId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
    }

    public String actorId() { return actorId; }
    public String operation() { return operation; }
    public String idempotencyKey() { return idempotencyKey; }
}
