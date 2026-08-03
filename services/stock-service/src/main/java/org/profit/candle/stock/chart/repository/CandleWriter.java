package org.profit.candle.stock.chart.repository;

import org.profit.candle.stock.chart.entity.CandleEntity;

import java.time.Instant;
import java.util.List;

public interface CandleWriter {
    <S extends CandleEntity> List<S> saveAll(Iterable<S> candles);

    /**
     * 특정 주기·시각의 미확정 캔들을 한 번에 마감 처리한다.
     *
     * @return 이번 호출에서 새로 확정한 캔들 수
     */
    int markClosedAt(String interval, Instant openTime);
}
