package org.profit.candle.stock.catalog.repository;

import org.profit.candle.stock.catalog.entity.StockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 종목 마스터 조회. */
public interface StockReader {

    Optional<StockEntity> findByStockCode(String stockCode);

    List<StockEntity> findByStockCodeIn(Collection<String> stockCodes);

    /** 조건검색 + 페이징. 필터가 null 이면 무시된다. */
    Page<StockEntity> search(String query, String market, String sector, String status, Pageable pageable);
}
