package org.profit.candle.batch.trading.policy;

import java.util.function.Supplier;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.springframework.stereotype.Component;

@Component
public class TradingBatchRetryExecutor {

    private static final int MAX_ATTEMPTS = 3;

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (TradingBatchException exception) {
                if (!exception.retryable() || attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
