package org.profit.candle.trading.account.event;

import jakarta.persistence.*;
import org.profit.candle.common.security.EncryptedPayloadConverter;

import java.time.Instant;

/**
 * 멱등성 레코드 (스펙 §4). 성공 response protobuf bytes를 함께 저장해
 * 같은 키 재시도 시 재생(replay)한다. 도메인 변경·outbox와 한 트랜잭션에서 commit된다.
 */
@Entity(name = "AccountIdempotencyRecord")
@Table(name = "idempotency_records", schema="account")
public class IdempotencyRecord {

    @EmbeddedId
    private IdempotencyRecordId id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Convert(converter = EncryptedPayloadConverter.class)
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
