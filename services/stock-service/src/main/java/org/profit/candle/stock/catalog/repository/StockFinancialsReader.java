package org.profit.candle.stock.catalog.repository;

import org.profit.candle.stock.catalog.entity.StockFinancialsEntity;

import java.util.Optional;

/** 종목 재무지표 조회. */
public interface StockFinancialsReader {

    /** 가장 최근 회계기간의 재무 스냅샷. */
    Optional<StockFinancialsEntity> findLatestByStockId(Long stockId);
}
