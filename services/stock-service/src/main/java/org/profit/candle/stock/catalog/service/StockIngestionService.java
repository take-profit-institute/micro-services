package org.profit.candle.stock.catalog.service;

import org.profit.candle.stock.catalog.dto.StockResult;

import java.util.Optional;

/** 키움 기준정보를 DB에 적재(upsert)한다. */
public interface StockIngestionService {

    /** 단일 종목을 키움에서 조회해 upsert. 조회 실패 시 empty. */
    Optional<StockResult> fetchAndSave(String code);

    /** 시장 전체를 키움에서 pull 하여 벌크 upsert. upsert 건수 반환. */
    int syncMarket(String marketType);
}
