package org.profit.candle.trading.idempotency;

import io.grpc.Context;

/**
 * 인터셉터가 metadata에서 추출한 멱등성 컨텍스트 (스펙 §6).
 *
 * actorId = 인증된 x-user-id, operation = 전체 gRPC method 이름,
 * idempotencyKey = x-idempotency-key (쓰기 RPC에서만 존재).
 */
public record IdempotencyContext(String actorId, String operation, String idempotencyKey) {

    /** gRPC Context 전파 키 — 인터셉터가 set, 서비스(executor)가 read. */
    public static final Context.Key<IdempotencyContext> CONTEXT_KEY =
            Context.key("candle.idempotency-context");

    public static IdempotencyContext current() {
        return CONTEXT_KEY.get();
    }
}
