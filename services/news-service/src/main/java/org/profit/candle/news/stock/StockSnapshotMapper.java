package org.profit.candle.news.stock;

import org.profit.candle.proto.stock.v1.Stock;
import org.profit.candle.proto.stock.v1.StockDetail;

final class StockSnapshotMapper {
    private StockSnapshotMapper() {
        throw new AssertionError("Utility class");
    }

    static StockSnapshot fromProto(StockDetail detail) {
        Stock stock = detail.getStock();
        return new StockSnapshot(
                stock.getCode(),
                stock.getName(),
                stock.getMarket().name(),
                stock.getSector(),
                stock.getMarketCap(),
                stock.getSharesOutstanding(),
                stock.getStatus().name()
        );
    }
}
