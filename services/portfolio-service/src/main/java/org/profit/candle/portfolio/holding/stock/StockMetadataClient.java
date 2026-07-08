package org.profit.candle.portfolio.holding.stock;

import java.util.Collection;
import java.util.Map;

public interface StockMetadataClient {
    Map<String, StockMetadata> getMetadata(Collection<String> symbols);

    default StockMetadata getMetadata(String symbol) {
        return getMetadata(java.util.List.of(symbol)).getOrDefault(symbol, StockMetadata.EMPTY);
    }
}
