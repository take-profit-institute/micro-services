package org.profit.candle.batch.stock.sync.client;

public interface StockSyncClient {

    Result sync(Market market);

    enum Market {
        KOSPI,
        KOSDAQ
    }

    record Result(int upserted, int total) {
    }
}
