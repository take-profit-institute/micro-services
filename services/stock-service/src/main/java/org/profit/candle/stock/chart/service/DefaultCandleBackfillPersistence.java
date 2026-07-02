package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.repository.CandleWriter;
import org.profit.candle.stock.client.KiwoomCandleData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        Instant now = Instant.now();
        List<CandleEntity> upserts = fetched.stream()
                .map(data -> {
                    CandleEntity candle = new CandleEntity(data.code(), data.interval().storageValue(), data.openTime());
                    // 진행 중인(현재 주기) 캔들은 closed=false. EOD 배치가 확정 종가로 true 전환 + 이벤트 발행한다.
                    boolean closed = data.interval().isPeriodClosed(data.openTime(), now);
                    candle.applyPrices(data.open(), data.high(), data.low(), data.close(), data.volume(), closed, "KIWOOM");
                    return candle;
                })
                .toList();

        candleWriter.saveAll(upserts);
        return upserts.size();
    }
}
