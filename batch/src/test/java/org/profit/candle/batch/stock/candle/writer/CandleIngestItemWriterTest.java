package org.profit.candle.batch.stock.candle.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.stock.candle.exception.CandleIngestFailureLimitExceededException;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.springframework.batch.infrastructure.item.Chunk;

class CandleIngestItemWriterTest {

    @Test
    void write_countsFailuresWithoutThrowingUnderLimit() {
        CandleIngestItemWriter writer = new CandleIngestItemWriter(2);

        assertThatCode(() -> writer.write(chunk(
                CandleIngestResult.succeeded("000001", 5),
                CandleIngestResult.failed("000002", "boom"),
                CandleIngestResult.succeeded("000003", 0)
        ))).doesNotThrowAnyException();

        assertThat(writer.failureCount()).isEqualTo(1);
    }

    @Test
    void write_failsJobWhenCumulativeFailuresExceedLimit() {
        CandleIngestItemWriter writer = new CandleIngestItemWriter(2);

        writer.write(chunk(
                CandleIngestResult.failed("000001", "boom"),
                CandleIngestResult.failed("000002", "boom")
        ));

        // 상한은 누적이므로 다음 청크에서 초과한다.
        assertThatThrownBy(() -> writer.write(chunk(CandleIngestResult.failed("000003", "boom"))))
                .isInstanceOf(CandleIngestFailureLimitExceededException.class)
                .hasMessageContaining("failures=3")
                .hasMessageContaining("limit=2");
    }

    private static Chunk<CandleIngestResult> chunk(CandleIngestResult... results) {
        return new Chunk<>(List.of(results));
    }
}
