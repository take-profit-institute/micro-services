package org.profit.candle.stock.catalog.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.catalog.dto.StockDataSource;
import org.profit.candle.stock.catalog.dto.StockDetailResult;
import org.profit.candle.stock.catalog.dto.StockFinancialsResult;
import org.profit.candle.stock.catalog.dto.StockResult;
import org.profit.candle.stock.catalog.dto.StockSearchCriteria;
import org.profit.candle.stock.catalog.entity.StockEntity;
import org.profit.candle.stock.catalog.exception.StockErrorCode;
import org.profit.candle.stock.catalog.repository.StockFinancialsReader;
import org.profit.candle.stock.catalog.repository.StockReader;
import org.profit.candle.stock.config.KiwoomProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DefaultStockCatalogService implements StockCatalogService {

    private final StockReader stockReader;
    private final StockFinancialsReader financialsReader;
    private final StockIngestionService ingestionService;
    private final KiwoomProperties kiwoomProperties;

    @Override
    @Transactional(readOnly = true)
    public Page<StockResult> search(StockSearchCriteria criteria, Pageable pageable) {
        return stockReader.search(criteria.query(), criteria.market(), criteria.sector(),
                criteria.status(), pageable).map(StockResult::from);
    }

    @Override
    // 트랜잭션을 걸지 않는다 — 키움 fallback(HTTP) 동안 DB 커넥션을 잡지 않기 위함.
    // 조회 엔티티에 지연로딩 연관이 없어 open-in-view=false 에서도 안전하다.
    public StockDetailResult getStock(String code, boolean allowFallback) {
        Optional<StockEntity> dbStock = stockReader.findByStockCode(code);
        boolean stale = dbStock.map(this::stale).orElse(true);

        if (allowFallback && stale) {
            Optional<StockResult> fetched = ingestionService.fetchAndSave(code);
            if (fetched.isPresent()) {
                return new StockDetailResult(fetched.get(), null, StockDataSource.KIWOOM);
            }
        }

        StockEntity stock = dbStock.orElseThrow(() -> new CandleException(StockErrorCode.STOCK_NOT_FOUND));
        StockFinancialsResult financials = financialsReader.findLatestByStockId(stock.stockId())
                .map(StockFinancialsResult::from)
                .orElse(null);
        return new StockDetailResult(StockResult.from(stock), financials, StockDataSource.DB);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockResult> batchGet(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return stockReader.findByStockCodeIn(codes).stream().map(StockResult::from).toList();
    }

    private boolean stale(StockEntity stock) {
        Instant synced = stock.syncedAt();
        return synced == null || synced.isBefore(Instant.now().minus(kiwoomProperties.staleness()));
    }
}
