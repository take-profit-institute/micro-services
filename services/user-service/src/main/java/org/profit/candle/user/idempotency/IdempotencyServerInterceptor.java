package org.profit.candle.user.idempotency;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.regex.Pattern;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class IdempotencyServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> IDEMPOTENCY_KEY =
            Metadata.Key.of("x-idempotency-key", Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Pattern UUID_CANONICAL =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_KEY_LENGTH = 64;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String idempotencyKey = headers.get(IDEMPOTENCY_KEY);
        if (idempotencyKey != null && !isValidKey(idempotencyKey)) {
            call.close(
                    Status.INVALID_ARGUMENT.withDescription("IDEMPOTENCY_KEY_INVALID"),
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String actorId = headers.get(USER_ID);
        String operation = call.getMethodDescriptor().getFullMethodName();
        IdempotencyContext context = new IdempotencyContext(actorId, operation, idempotencyKey);

        Context grpcContext = Context.current().withValue(IdempotencyContext.CONTEXT_KEY, context);
        return Contexts.interceptCall(grpcContext, call, headers, next);
    }

    private boolean isValidKey(String key) {
        return key.length() <= MAX_KEY_LENGTH && UUID_CANONICAL.matcher(key).matches();
    }
}
