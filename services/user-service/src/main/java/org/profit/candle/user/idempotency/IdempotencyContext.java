package org.profit.candle.user.idempotency;

import io.grpc.Context;

public record IdempotencyContext(String actorId, String operation, String idempotencyKey) {

    public static final Context.Key<IdempotencyContext> CONTEXT_KEY =
            Context.key("candle.user.idempotency-context");

    public static IdempotencyContext current() {
        return CONTEXT_KEY.get();
    }
}
