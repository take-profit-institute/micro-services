package org.profit.candle.stock.catalog.repository;

import org.profit.candle.stock.catalog.entity.StockEntity;

/** 종목 마스터 저장. */
public interface StockWriter {

    StockEntity save(StockEntity stock);
}
