package org.profit.candle.stock.chart.service;

import org.profit.candle.stock.client.KiwoomCandleData;

import java.util.List;

public interface CandleBackfillPersistence {
    int upsertFetched(List<KiwoomCandleData> fetched);
}
