package org.profit.candle.stock.catalog.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.catalog.dto.StockResult;
import org.profit.candle.stock.catalog.entity.StockEntity;
import org.profit.candle.stock.catalog.repository.StockReader;
import org.profit.candle.stock.catalog.repository.StockWriter;
import org.profit.candle.stock.client.KiwoomStockClient;
import org.profit.candle.stock.client.KiwoomStockData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DefaultStockIngestionService implements StockIngestionService {

    private final KiwoomStockClient kiwoomStockClient;
    private final StockReader stockReader;
    private final StockWriter stockWriter;

    @Override
    @Transactional
    public Optional<StockResult> fetchAndSave(String code) {
        return kiwoomStockClient.findStock(code).map(this::upsert).map(StockResult::from);
    }

    @Override
    @Transactional
    public int syncMarket(String marketType) {
        List<KiwoomStockData> stocks = kiwoomStockClient.findAllStocksByMarket(marketType);
        stocks.forEach(this::upsert);
        return stocks.size();
    }

    private StockEntity upsert(KiwoomStockData data) {
        String market = data.marketType() != null ? data.marketType() : "KOSPI";
        StockEntity entity = stockReader.findByStockCode(data.code())
                .orElseGet(() -> new StockEntity(data.code(), data.name(), market));
        entity.applyReferenceData(data.name(), data.marketType(), data.sector(), data.marketCap(),
                data.sharesOutstanding(), data.listedAt(), data.listingStatus(), "KIWOOM");
        return stockWriter.save(entity);
    }
}
