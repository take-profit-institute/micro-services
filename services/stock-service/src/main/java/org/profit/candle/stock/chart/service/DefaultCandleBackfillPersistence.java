package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.repository.CandleWriter;
import org.profit.candle.stock.client.KiwoomCandleData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultCandleBackfillPersistence implements CandleBackfillPersistence {

    private final CandleWriter candleWriter;

    @Override
    @Transactional
    public int upsertFetched(List<KiwoomCandleData> fetched) {
        if (fetched.isEmpty()) {
            return 0;
        }
        List<CandleEntity> upserts = fetched.stream()
                .map(data -> {
                    CandleEntity candle = new CandleEntity(data.code(), data.interval().storageValue(), data.openTime());
                    candle.applyPrices(data.open(), data.high(), data.low(), data.close(), data.volume(), true, "KIWOOM");
                    return candle;
                })
                .toList();

        candleWriter.saveAll(upserts);
        return upserts.size();
    }
}
