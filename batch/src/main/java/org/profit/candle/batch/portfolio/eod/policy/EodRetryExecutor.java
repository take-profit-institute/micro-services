package org.profit.candle.batch.portfolio.eod.policy;

import java.util.function.Supplier;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.springframework.stereotype.Component;

@Component
public class EodRetryExecutor {

    private static final int MAX_ATTEMPTS = 3;

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (EodBatchException exception) {
                if (!exception.retryable() || attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
