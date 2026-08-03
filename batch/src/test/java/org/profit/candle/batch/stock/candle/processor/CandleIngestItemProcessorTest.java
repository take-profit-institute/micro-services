package org.profit.candle.batch.stock.candle.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.exception.StockCandleErrorCode;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.profit.candle.batch.stock.candle.policy.StockCandleRetryExecutor;

@ExtendWith(MockitoExtension.class)
class CandleIngestItemProcessorTest {

    private static final int CANDLE_COUNT = 10;

    @Mock CandleBackfillClient backfillClient;

    private CandleIngestItemProcessor processor() {
        // 백오프 없이 재시도 횟수만 검증한다.
        return new CandleIngestItemProcessor(
                backfillClient,
                new StockCandleRetryExecutor(Duration.ZERO),
                CANDLE_COUNT
        );
    }

    @Test
    void process_returnsUpsertedCountOnSuccess() {
        when(backfillClient.backfillDaily("000001", CANDLE_COUNT)).thenReturn(7);

        CandleIngestResult result = processor().process("000001");

        assertThat(result.failed()).isFalse();
        assertThat(result.code()).isEqualTo("000001");
        assertThat(result.upserted()).isEqualTo(7);
    }

    @Test
    void process_returnsFailedResultInsteadOfThrowing() {
        when(backfillClient.backfillDaily("000002", CANDLE_COUNT))
                .thenThrow(new StockCandleException(StockCandleErrorCode.EXTERNAL_CLIENT_FAILED, null));

        CandleIngestResult result = processor().process("000002");

        // 던지면 청크가 롤백되고 청크 전체의 키움 호출이 1건씩 재실행된다.
        assertThat(result.failed()).isTrue();
        assertThat(result.code()).isEqualTo("000002");
        assertThat(result.upserted()).isZero();
        verify(backfillClient).backfillDaily("000002", CANDLE_COUNT);
    }

    @Test
    void process_retriesRetryableFailureThenAbsorbsIt() {
        when(backfillClient.backfillDaily("000003", CANDLE_COUNT))
                .thenThrow(new StockCandleException(StockCandleErrorCode.EXTERNAL_CLIENT_RETRYABLE, null));

        CandleIngestResult result = processor().process("000003");

        assertThat(result.failed()).isTrue();
        verify(backfillClient, times(3)).backfillDaily("000003", CANDLE_COUNT);
    }

    @Test
    void process_doesNotRetryKiwoomRateLimit() {
        when(backfillClient.backfillDaily("000004", CANDLE_COUNT))
                .thenThrow(new StockCandleException(StockCandleErrorCode.EXTERNAL_RATE_LIMITED, null));

        CandleIngestResult result = processor().process("000004");

        // stock-service가 이미 429 백오프를 소진한 뒤라 여기서 또 때리면 예산만 태운다.
        assertThat(result.failed()).isTrue();
        verify(backfillClient, times(1)).backfillDaily("000004", CANDLE_COUNT);
    }
}
