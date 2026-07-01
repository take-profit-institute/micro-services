package org.profit.candle.stock.chart.repository;

import org.profit.candle.stock.chart.entity.CandleEntity;

import java.util.List;

public interface CandleWriter {
    <S extends CandleEntity> List<S> saveAll(Iterable<S> candles);
}
