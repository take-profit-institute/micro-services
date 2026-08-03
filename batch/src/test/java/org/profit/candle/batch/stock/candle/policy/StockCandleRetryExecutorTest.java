package org.profit.candle.batch.stock.candle.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.stock.candle.exception.StockCandleErrorCode;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;

class StockCandleRetryExecutorTest {

    @Test
    void execute_returnsResultWithoutRetryOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        StockCandleRetryExecutor executor = new StockCandleRetryExecutor(Duration.ZERO);

        int result = executor.execute(() -> {
            calls.incrementAndGet();
            return 42;
        });

        assertThat(result).isEqualTo(42);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void execute_retriesRetryableFailureUpToThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        StockCandleRetryExecutor executor = new StockCandleRetryExecutor(Duration.ZERO);

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new StockCandleException(StockCandleErrorCode.EXTERNAL_CLIENT_RETRYABLE, null);
        })).isInstanceOf(StockCandleException.class);

        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void execute_doesNotRetryNonRetryableFailure() {
        AtomicInteger calls = new AtomicInteger();
        StockCandleRetryExecutor executor = new StockCandleRetryExecutor(Duration.ZERO);

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new StockCandleException(StockCandleErrorCode.EXTERNAL_CLIENT_FAILED, null);
        })).isInstanceOf(StockCandleException.class);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void execute_doesNotRetryKiwoomRateLimit() {
        AtomicInteger calls = new AtomicInteger();
        StockCandleRetryExecutor executor = new StockCandleRetryExecutor(Duration.ZERO);

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new StockCandleException(StockCandleErrorCode.EXTERNAL_RATE_LIMITED, null);
        })).isInstanceOf(StockCandleException.class);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void execute_waitsBetweenRetriesInsteadOfSpinning() {
        StockCandleRetryExecutor executor = new StockCandleRetryExecutor(Duration.ofMillis(50));

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> executor.execute(() -> {
            throw new StockCandleException(StockCandleErrorCode.EXTERNAL_CLIENT_RETRYABLE, null);
        })).isInstanceOf(StockCandleException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // 재시도 2회 = 50ms + 100ms(지수) + 지터. 즉시 재시도였다면 0에 가깝다.
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(150));
    }
}
