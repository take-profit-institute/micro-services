package org.profit.candle.portfolio.holding.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.repository.HoldingReader;
import org.profit.candle.portfolio.holding.repository.HoldingWriter;
import org.profit.candle.portfolio.holding.stock.StockMetadata;
import org.profit.candle.portfolio.holding.stock.StockMetadataClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingMetadataBackfillService {
    private final HoldingReader holdingReader;
    private final HoldingWriter holdingWriter;
    private final StockMetadataClient stockMetadataClient;

    @Transactional
    public int backfill(int batchSize) {
        List<HoldingEntity> holdings = holdingReader.findMetadataMissing(batchSize);
        if (holdings.isEmpty()) {
            return 0;
        }

        Map<String, StockMetadata> metadataBySymbol = stockMetadataClient.getMetadata(
                holdings.stream().map(HoldingEntity::symbol).toList());

        int updated = 0;
        for (HoldingEntity holding : holdings) {
            StockMetadata metadata = metadataBySymbol.getOrDefault(holding.symbol(), StockMetadata.EMPTY);
            if (metadata.isBlank()) {
                continue;
            }
            holding.enrichMetadata(metadata.name(), metadata.sector(), metadata.market());
            holdingWriter.save(holding);
            updated++;
        }

        log.info("Holding metadata backfill batch finished. candidates={}, updated={}", holdings.size(), updated);
        return updated;
    }
}
