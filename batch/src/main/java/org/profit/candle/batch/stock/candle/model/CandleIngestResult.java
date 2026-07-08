package org.profit.candle.batch.stock.candle.model;

/** 한 종목의 일봉 백필 결과. */
public record CandleIngestResult(String code, int upserted) {
}
