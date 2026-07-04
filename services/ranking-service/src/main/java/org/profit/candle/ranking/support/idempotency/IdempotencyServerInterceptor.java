package org.profit.candle.ranking.support.idempotency;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.util.regex.Pattern;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class IdempotencyServerInterceptor implements io.grpc.ServerInterceptor {

    static final Metadata.Key<String> IDEMPOTENCY_KEY =
            Metadata.Key.of("x-idempotency-key", Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Pattern UUID_CANONICAL = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** metadata 형식을 검증하고 executor가 사용할 호출 정보를 gRPC Context에 저장한다. */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String idempotencyKey = headers.get(IDEMPOTENCY_KEY);
        if (idempotencyKey != null && !UUID_CANONICAL.matcher(idempotencyKey).matches()) {
            call.close(Status.INVALID_ARGUMENT.withDescription("IDEMPOTENCY_KEY_INVALID"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        IdempotencyContext context = new IdempotencyContext(
                headers.get(USER_ID),
                call.getMethodDescriptor().getFullMethodName(),
                idempotencyKey);
        Context grpcContext = Context.current().withValue(IdempotencyContext.CONTEXT_KEY, context);
        return Contexts.interceptCall(grpcContext, call, headers, next);
    }
}
