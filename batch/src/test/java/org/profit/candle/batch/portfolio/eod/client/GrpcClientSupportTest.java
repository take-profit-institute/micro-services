package org.profit.candle.batch.portfolio.eod.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;

class GrpcClientSupportTest {

    @Test
    void shouldMapUnavailableToRetryableException() {
        EodBatchException result = GrpcClientSupport.mapException(
                Status.UNAVAILABLE.asRuntimeException()
        );

        assertThat(result.retryable()).isTrue();
    }

    @Test
    void shouldMapInvalidArgumentToNonRetryableException() {
        EodBatchException result = GrpcClientSupport.mapException(
                Status.INVALID_ARGUMENT.asRuntimeException()
        );

        assertThat(result.retryable()).isFalse();
    }
}
