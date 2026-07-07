package org.profit.candle.batch.ranking.policy;

import java.util.function.Supplier;
import org.profit.candle.batch.ranking.exception.RankingBatchException;
import org.springframework.stereotype.Component;

/** Ranking Service의 일시적 장애만 최대 세 번 호출한다. */
@Component
public class RankingBatchRetryExecutor {

    private static final int MAX_ATTEMPTS = 3;

    /** 같은 명령과 멱등성 키를 유지하면서 재시도한다. */
    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RankingBatchException exception) {
                if (!exception.retryable() || attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
