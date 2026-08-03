package org.profit.candle.batch.stock.candle.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        return new CandleIngestItemProcessor(backfillClient, new StockCandleRetryExecutor(), CANDLE_COUNT);
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
        verify(backfillClient, org.mockito.Mockito.times(3)).backfillDaily("000003", CANDLE_COUNT);
    }
}
