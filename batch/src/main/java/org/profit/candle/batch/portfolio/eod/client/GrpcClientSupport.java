package org.profit.candle.batch.portfolio.eod.client;

import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;

public final class GrpcClientSupport {

    private static final Metadata.Key<String> USER_ID = key("x-user-id");
    private static final Metadata.Key<String> ROLE = key("x-role");
    private static final Metadata.Key<String> REQUEST_ID = key("x-request-id");
    private static final Metadata.Key<String> IDEMPOTENCY_KEY = key("x-idempotency-key");

    private GrpcClientSupport() {
        throw new AssertionError("Utility class");
    }

    public static ClientInterceptor systemRead(String requestId) {
        return attach(baseMetadata(requestId));
    }

    public static ClientInterceptor userRead(String userId, String requestId) {
        Metadata metadata = baseMetadata(requestId);
        metadata.put(USER_ID, userId);
        return attach(metadata);
    }

    public static ClientInterceptor userWrite(
            String userId,
            String requestId,
            String idempotencyKey
    ) {
        Metadata metadata = baseMetadata(requestId);
        metadata.put(USER_ID, userId);
        metadata.put(IDEMPOTENCY_KEY, idempotencyKey);
        return attach(metadata);
    }

    public static EodBatchException mapException(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        boolean retryable = code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED;

        EodBatchErrorCode errorCode = retryable
                ? EodBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE
                : EodBatchErrorCode.EXTERNAL_CLIENT_FAILED;
        return new EodBatchException(errorCode, exception);
    }

    private static ClientInterceptor attach(Metadata metadata) {
        return MetadataUtils.newAttachHeadersInterceptor(metadata);
    }

    private static Metadata baseMetadata(String requestId) {
        Metadata metadata = new Metadata();
        metadata.put(ROLE, "SYSTEM");
        metadata.put(REQUEST_ID, requestId);
        return metadata;
    }

    private static Metadata.Key<String> key(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }
}
