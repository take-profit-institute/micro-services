package org.profit.candle.stock.catalog.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.catalog.dto.StockResult;
import org.profit.candle.stock.catalog.entity.StockEntity;
import org.profit.candle.stock.catalog.repository.StockReader;
import org.profit.candle.stock.catalog.repository.StockWriter;
import org.profit.candle.stock.client.KiwoomStockClient;
import org.profit.candle.stock.client.KiwoomStockData;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 키움(외부 HTTP) 조회는 트랜잭션 밖에서 수행한다 — DB 트랜잭션/커넥션을 네트워크 IO 동안
 * 잡지 않기 위함(장애 시 커넥션 고갈·락 점유 방지). 실제 DB 쓰기는 {@link StockWriter#save}
 * 호출마다 각자의 트랜잭션으로 커밋된다(idempotent upsert라 부분 성공도 재실행으로 수렴).
 */
@Service
@RequiredArgsConstructor
public class DefaultStockIngestionService implements StockIngestionService {

    private final KiwoomStockClient kiwoomStockClient;
    private final StockReader stockReader;
    private final StockWriter stockWriter;

    @Override
    public Optional<StockResult> fetchAndSave(String code) {
        return kiwoomStockClient.findStock(code).map(this::upsert).map(StockResult::from);
    }

    @Override
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
        return saveResilient(entity, data);
    }

    private StockEntity saveResilient(StockEntity entity, KiwoomStockData data) {
        try {
            return stockWriter.save(entity);
        } catch (DataIntegrityViolationException e) {
            StockEntity existing = stockReader.findByStockCode(data.code()).orElseThrow(() -> e);
            existing.applyReferenceData(data.name(), data.marketType(), data.sector(), data.marketCap(),
                    data.sharesOutstanding(), data.listedAt(), data.listingStatus(), "KIWOOM");
            return stockWriter.save(existing);
        }
    }
}
