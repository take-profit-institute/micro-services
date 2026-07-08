package org.profit.candle.batch.stock.candle.listener;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.springframework.batch.core.listener.SkipListener;

/**
 * 백필에 실패해 skip된 종목을 관측용으로 남긴다. skip은 잡을 죽이지 않고 다음 종목으로 넘어가며,
 * 누적 skip이 skipLimit을 넘으면(=대량 장애) 그때 잡이 FAILED로 떨어진다.
 */
@Slf4j
public class CandleIngestSkipListener implements SkipListener<String, CandleIngestResult> {

    @Override
    public void onSkipInProcess(String code, Throwable throwable) {
        log.warn("[Stock Candle Ingest] skip code={} reason={}", code, throwable.toString());
    }

    @Override
    public void onSkipInRead(Throwable throwable) {
        log.warn("[Stock Candle Ingest] skip on read reason={}", throwable.toString());
    }

    @Override
    public void onSkipInWrite(CandleIngestResult item, Throwable throwable) {
        log.warn("[Stock Candle Ingest] skip on write code={} reason={}",
                item != null ? item.code() : "?", throwable.toString());
    }
}
