package org.profit.candle.batch.stock.candle.policy;

import java.util.function.Supplier;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.springframework.stereotype.Component;

/** 재시도 가능한 StockCandleException만 최대 3회 재시도한다(EodRetryExecutor와 동일 규약). */
@Component
public class StockCandleRetryExecutor {

    private static final int MAX_ATTEMPTS = 3;

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (StockCandleException exception) {
                if (!exception.retryable() || attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
