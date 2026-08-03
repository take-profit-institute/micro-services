package org.profit.candle.batch.stock.candle.listener;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.springframework.batch.core.listener.SkipListener;

/**
 * skip된 항목을 관측용으로 남긴다. 누적 skip이 skipLimit을 넘으면 그때 잡이 FAILED로 떨어진다.
 *
 * <p>종목 단위 백필 실패는 더 이상 여기로 오지 않는다 — processor가 예외 대신 실패 결과를 돌려주고
 * writer가 집계/상한 판정을 한다(청크 롤백 후 키움 호출이 통째로 재실행되는 것을 막기 위함).
 * 실질적으로 남는 경로는 카탈로그 페이지 조회(read) 실패다.
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
