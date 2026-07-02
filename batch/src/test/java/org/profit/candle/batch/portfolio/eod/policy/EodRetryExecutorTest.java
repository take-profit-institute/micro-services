package org.profit.candle.batch.portfolio.eod.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;

class EodRetryExecutorTest {

    private final EodRetryExecutor retryExecutor = new EodRetryExecutor();

    @Test
    void shouldRetryRetryableReadAtMostThreeTimes() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new EodBatchException(
                    EodBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE,
                    new IllegalStateException()
            );
        })).isInstanceOf(EodBatchException.class);

        assertThat(attempts).hasValue(3);
    }

    @Test
    void shouldNotRetryNonRetryableFailure() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            throw new EodBatchException(EodBatchErrorCode.CLOSING_PRICE_INVALID);
        })).isInstanceOf(EodBatchException.class);

        assertThat(attempts).hasValue(1);
    }
}
