package org.profit.candle.stock.catalog.repository;

import org.profit.candle.stock.catalog.entity.StockFinancialsEntity;
import org.profit.candle.stock.catalog.entity.StockFinancialsId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaStockFinancialsRepository
        extends JpaRepository<StockFinancialsEntity, StockFinancialsId>, StockFinancialsReader {

    Optional<StockFinancialsEntity> findTopById_StockIdOrderById_FiscalPeriodDesc(Long stockId);

    @Override
    default Optional<StockFinancialsEntity> findLatestByStockId(Long stockId) {
        return findTopById_StockIdOrderById_FiscalPeriodDesc(stockId);
    }
}
