package org.profit.candle.batch.portfolio.eod.writer;

import org.profit.candle.batch.portfolio.eod.client.PortfolioSnapshotClient;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

public class SnapshotItemWriter implements ItemWriter<SnapshotCommand> {

    private final PortfolioSnapshotClient snapshotClient;

    public SnapshotItemWriter(PortfolioSnapshotClient snapshotClient) {
        this.snapshotClient = snapshotClient;
    }

    @Override
    public void write(Chunk<? extends SnapshotCommand> chunk) {
        for (SnapshotCommand command : chunk) {
            snapshotClient.recordDailySnapshot(command);
        }
    }
}
