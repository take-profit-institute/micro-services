package org.profit.candle.user.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_idempotency_records")
public class IdempotencyRecord {

    @EmbeddedId
    private IdempotencyRecordId id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_payload", nullable = false)
    private byte[] responsePayload;

    @Column(name = "response_type", nullable = false, length = 200)
    private String responseType;

    @Column(name = "grpc_code", nullable = false, length = 40)
    private String grpcCode;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(IdempotencyRecordId id, String requestHash, byte[] responsePayload,
                             String responseType, String grpcCode, Instant expiresAt) {
        this.id = id;
        this.requestHash = requestHash;
        this.responsePayload = responsePayload;
        this.responseType = responseType;
        this.grpcCode = grpcCode;
        this.expiresAt = expiresAt;
    }

    public IdempotencyRecordId id() { return id; }
    public String requestHash() { return requestHash; }
    public byte[] responsePayload() { return responsePayload; }
    public String responseType() { return responseType; }
    public String grpcCode() { return grpcCode; }
    public Instant expiresAt() { return expiresAt; }
}
