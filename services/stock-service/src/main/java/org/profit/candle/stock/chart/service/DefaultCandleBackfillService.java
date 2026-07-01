package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.chart.repository.CandleWriter;
import org.profit.candle.stock.client.KiwoomCandleData;
import org.profit.candle.stock.client.KiwoomChartClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultCandleBackfillService implements CandleBackfillService {

    private final KiwoomChartClient chartClient;
    private final CandleReader candleReader;
    private final CandleWriter candleWriter;

    @Override
    @Transactional
    public int backfill(String code, CandleInterval interval, int count) {
        List<KiwoomCandleData> fetched = chartClient.fetchCandles(code, interval, count);
        if (fetched.isEmpty()) {
            return 0;
        }

        Map<String, CandleEntity> existing = candleReader.findLatest(code, interval.storageValue(), null, count).stream()
                .collect(Collectors.toMap(c -> keyOf(c.id().stockCode(), c.id().interval(), c.id().openTime().toString()),
                        Function.identity()));

        List<CandleEntity> upserts = fetched.stream()
                .map(data -> {
                    String key = keyOf(data.code(), data.interval().storageValue(), data.openTime().toString());
                    CandleEntity candle = existing.getOrDefault(key,
                            new CandleEntity(data.code(), data.interval().storageValue(), data.openTime()));
                    candle.applyPrices(data.open(), data.high(), data.low(), data.close(), data.volume(), true, "KIWOOM");
                    return candle;
                })
                .toList();

        candleWriter.saveAll(upserts);
        return upserts.size();
    }

    private static String keyOf(String code, String interval, String openTime) {
        return code + ":" + interval + ":" + openTime;
    }
}
