package org.profit.candle.batch.portfolio.eod.client;

import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;

public interface PortfolioSnapshotClient {

    void recordDailySnapshot(SnapshotCommand command);
}
