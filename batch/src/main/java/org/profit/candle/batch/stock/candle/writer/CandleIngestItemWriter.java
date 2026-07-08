package org.profit.candle.batch.stock.candle.writer;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * 백필은 processor가 stock-service에서 이미 커밋했으므로 writer는 집계 로깅만 한다
 * (0건 종목이 많으면 키움 응답이 비었다는 신호 — 관측용).
 */
@Slf4j
public class CandleIngestItemWriter implements ItemWriter<CandleIngestResult> {

    @Override
    public void write(Chunk<? extends CandleIngestResult> chunk) {
        int symbols = chunk.size();
        int upsertedTotal = 0;
        int emptySymbols = 0;
        for (CandleIngestResult result : chunk) {
            upsertedTotal += result.upserted();
            if (result.upserted() == 0) {
                emptySymbols++;
            }
        }
        log.info(
                "[Stock Candle Ingest] chunk symbols={}, upsertedCandles={}, emptySymbols={}",
                symbols,
                upsertedTotal,
                emptySymbols
        );
    }
}
