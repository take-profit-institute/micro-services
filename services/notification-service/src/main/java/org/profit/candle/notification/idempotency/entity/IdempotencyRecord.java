package org.profit.candle.notification.idempotency.entity;

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
@Table(name = "idempotency_records", schema = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String operation;

    @Column(name = "idempotency_key", nullable = false, columnDefinition = "text")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, columnDefinition = "text")
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "response_type", nullable = false, columnDefinition = "text")
    private String responseType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IdempotencyRecord create(
            UUID userId,
            String operation,
            String idempotencyKey,
            String requestHash,
            String responseJson,
            String responseType
    ) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.id = UUID.randomUUID();
        record.userId = userId;
        record.operation = operation;
        record.idempotencyKey = idempotencyKey;
        record.requestHash = requestHash;
        record.responseJson = responseJson;
        record.responseType = responseType;
        record.createdAt = Instant.now();
        return record;
    }
}
