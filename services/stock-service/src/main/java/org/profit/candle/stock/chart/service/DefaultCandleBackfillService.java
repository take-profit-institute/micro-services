package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.client.KiwoomCandleData;
import org.profit.candle.stock.client.KiwoomChartClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultCandleBackfillService implements CandleBackfillService {

    private final KiwoomChartClient chartClient;
    private final CandleBackfillPersistence persistence;

    @Override
    public int backfill(String code, CandleInterval interval, int count, Instant to) {
        List<KiwoomCandleData> fetched = chartClient.fetchCandles(code, interval, count, to);
        return persistence.upsertFetched(fetched);
    }
}
