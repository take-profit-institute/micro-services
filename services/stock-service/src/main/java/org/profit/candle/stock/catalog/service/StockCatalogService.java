package org.profit.candle.stock.catalog.service;

import org.profit.candle.stock.catalog.dto.StockDetailResult;
import org.profit.candle.stock.catalog.dto.StockResult;
import org.profit.candle.stock.catalog.dto.StockSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockCatalogService {

    /** 조건검색 + 페이징 (DB 전용). */
    Page<StockResult> search(StockSearchCriteria criteria, Pageable pageable);

    /** 상세 조회. DB miss/stale 이고 allowFallback 이면 키움에서 조회·적재 후 반환. */
    StockDetailResult getStock(String code, boolean allowFallback);

    /** 코드 목록으로 벌크 조회. */
    List<StockResult> batchGet(List<String> codes);
}
