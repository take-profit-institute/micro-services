package org.profit.candle.batch.portfolio.eod.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.client.PortfolioSnapshotClient;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.springframework.batch.infrastructure.item.Chunk;

class SnapshotItemWriterTest {

    @Test
    void shouldNotAutomaticallyRetryPortfolioWrite() {
        AtomicInteger attempts = new AtomicInteger();
        PortfolioSnapshotClient client = command -> {
            attempts.incrementAndGet();
            throw new EodBatchException(
                    EodBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE,
                    new IllegalStateException()
            );
        };
        SnapshotItemWriter writer = new SnapshotItemWriter(client);
        SnapshotCommand command = new SnapshotCommand(
                "user-1",
                LocalDate.of(2026, 6, 29),
                100,
                50,
                100,
                "50f1e44e-8b98-4de7-a949-28ee63038a4d"
        );

        assertThatThrownBy(() -> writer.write(Chunk.of(command)))
                .isInstanceOf(EodBatchException.class);
        assertThat(attempts).hasValue(1);
    }
}
