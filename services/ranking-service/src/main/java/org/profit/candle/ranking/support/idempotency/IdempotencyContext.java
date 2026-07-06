package org.profit.candle.ranking.support.idempotency;

import io.grpc.Context;

public record IdempotencyContext(String actorId, String operation, String idempotencyKey) {

    public static final Context.Key<IdempotencyContext> CONTEXT_KEY =
            Context.key("candle.ranking.idempotency-context");

    /** 현재 gRPC 호출에 저장된 멱등성 정보를 반환한다. */
    public static IdempotencyContext current() {
        return CONTEXT_KEY.get();
    }
}
