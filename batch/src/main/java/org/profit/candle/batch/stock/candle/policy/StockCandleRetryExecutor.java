package org.profit.candle.batch.stock.candle.policy;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.springframework.stereotype.Component;

/**
 * 재시도 가능한 StockCandleException만 최대 3회 재시도한다(EodRetryExecutor와 동일 규약).
 *
 * <p>재시도 사이에 지수 백오프 + 지터를 둔다. 즉시 재시도는 상대가 이미 밀리고 있는 상황에서
 * 같은 실패를 그대로 재생산하고, stock-service 뒤의 키움은 초당 몇 건짜리 예산이라 특히 손해가 크다.
 * 키움 rate limit(RESOURCE_EXHAUSTED)은 stock-service가 이미 백오프를 소진한 신호라
 * 애초에 재시도 대상이 아니다({@code EXTERNAL_RATE_LIMITED}).
 */
@Component
public class StockCandleRetryExecutor {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(500);

    private final long backoffMillis;

    public StockCandleRetryExecutor() {
        this(DEFAULT_BACKOFF);
    }

    /** 백오프를 줄여 빠르게 검증하기 위한 생성자(테스트용). */
    public StockCandleRetryExecutor(Duration backoff) {
        this.backoffMillis = backoff.toMillis();
    }

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (StockCandleException exception) {
                if (!exception.retryable() || attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
                backoff(attempt);
            }
        }
    }

    private void backoff(int attempt) {
        if (backoffMillis <= 0) {
            return;
        }
        long delay = backoffMillis << (attempt - 1);
        // 워커 스레드들이 같은 순간에 몰려 재시도하지 않도록 흩는다.
        long jitter = ThreadLocalRandom.current().nextLong(backoffMillis + 1);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("캔들 적재 재시도 대기 중 인터럽트", e);
        }
    }
}
