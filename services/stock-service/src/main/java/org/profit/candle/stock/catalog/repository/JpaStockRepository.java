package org.profit.candle.stock.catalog.repository;

import org.profit.candle.stock.catalog.entity.StockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaStockRepository extends JpaRepository<StockEntity, Long>, StockReader, StockWriter {

    /**
     * 이름은 부분검색(대소문자 무시, trigram GIN 활용), 코드는 부분 매칭.
     * 안정 정렬을 위해 정렬 키가 같을 때의 보조 키(stock_code)는 호출부 Pageable 에서 부여한다.
     */
    @Override
    @Query("""
            SELECT s FROM StockEntity s
            WHERE (CAST(:market AS string) IS NULL OR s.marketType = :market)
              AND (CAST(:sector AS string) IS NULL OR s.sector = :sector)
              AND (CAST(:status AS string) IS NULL OR s.listingStatus = :status)
              AND (CAST(:query AS string) IS NULL
                   OR LOWER(s.stockName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                   OR s.stockCode LIKE CONCAT('%', CAST(:query AS string), '%'))
            """)
    Page<StockEntity> search(@Param("query") String query,
                             @Param("market") String market,
                             @Param("sector") String sector,
                             @Param("status") String status,
                             Pageable pageable);
}
