package org.profit.candle.batch.stock.sync.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.stock.sync.client.StockSyncClient;
import org.profit.candle.batch.stock.sync.exception.StockSyncErrorCode;
import org.profit.candle.batch.stock.sync.exception.StockSyncException;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class StockSyncTaskletTest {

    @Test
    void syncsKospiBeforeKosdaq() {
        RecordingClient client = new RecordingClient(0);
        StockSyncTasklet tasklet = new StockSyncTasklet(client);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(client.markets).containsExactly(
                StockSyncClient.Market.KOSPI,
                StockSyncClient.Market.KOSDAQ
        );
    }

    @Test
    void retriesRetryableFailureUpToSuccess() {
        RecordingClient client = new RecordingClient(2);
        StockSyncTasklet tasklet = new StockSyncTasklet(client);

        tasklet.execute(null, null);

        assertThat(client.markets).containsExactly(
                StockSyncClient.Market.KOSPI,
                StockSyncClient.Market.KOSPI,
                StockSyncClient.Market.KOSPI,
                StockSyncClient.Market.KOSDAQ
        );
    }

    @Test
    void failsAfterThreeRetryableFailures() {
        RecordingClient client = new RecordingClient(3);
        StockSyncTasklet tasklet = new StockSyncTasklet(client);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(StockSyncException.class);
        assertThat(client.markets).containsExactly(
                StockSyncClient.Market.KOSPI,
                StockSyncClient.Market.KOSPI,
                StockSyncClient.Market.KOSPI
        );
    }

    private static final class RecordingClient implements StockSyncClient {

        private final List<Market> markets = new ArrayList<>();
        private int remainingFailures;

        private RecordingClient(int remainingFailures) {
            this.remainingFailures = remainingFailures;
        }

        @Override
        public Result sync(Market market) {
            markets.add(market);
            if (remainingFailures > 0) {
                remainingFailures--;
                throw new StockSyncException(
                        StockSyncErrorCode.EXTERNAL_CLIENT_RETRYABLE,
                        new IllegalStateException("temporary failure")
                );
            }
            return new Result(10, 10);
        }
    }
}
