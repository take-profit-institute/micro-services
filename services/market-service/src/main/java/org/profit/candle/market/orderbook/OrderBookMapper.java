package org.profit.candle.market.orderbook;

import org.profit.candle.market.dto.response.KiwoomOrderBookResponse;

import java.time.Instant;
import java.util.List;

final class OrderBookMapper {
    private OrderBookMapper() {
        throw new AssertionError("Utility class");
    }

    static OrderBookSnapshot toSnapshot(String symbol, KiwoomOrderBookResponse response) {
        long ask1 = parseAbs(response.bestAskPrice());
        long askQty1 = parseAbs(response.bestAskQuantity());
        long bid1 = parseAbs(response.bestBidPrice());
        long bidQty1 = parseAbs(response.bestBidQuantity());
        return new OrderBookSnapshot(
                symbol,
                ask1,
                askQty1,
                bid1,
                bidQty1,
                parseAbs(response.totalAskQuantity()),
                parseAbs(response.totalBidQuantity()),
                Instant.now(),
                List.of(
                        level(1, ask1, askQty1, bid1, bidQty1),
                        level(2, response.ask2Price(), response.ask2Quantity(), response.bid2Price(), response.bid2Quantity()),
                        level(3, response.ask3Price(), response.ask3Quantity(), response.bid3Price(), response.bid3Quantity()),
                        level(4, response.ask4Price(), response.ask4Quantity(), response.bid4Price(), response.bid4Quantity()),
                        level(5, response.ask5Price(), response.ask5Quantity(), response.bid5Price(), response.bid5Quantity()),
                        level(6, response.ask6Price(), response.ask6Quantity(), response.bid6Price(), response.bid6Quantity()),
                        level(7, response.ask7Price(), response.ask7Quantity(), response.bid7Price(), response.bid7Quantity()),
                        level(8, response.ask8Price(), response.ask8Quantity(), response.bid8Price(), response.bid8Quantity()),
                        level(9, response.ask9Price(), response.ask9Quantity(), response.bid9Price(), response.bid9Quantity()),
                        level(10, response.ask10Price(), response.ask10Quantity(), response.bid10Price(), response.bid10Quantity())
                )
        );
    }

    private static OrderBookLevelSnapshot level(
            int level,
            String askPrice,
            String askQuantity,
            String bidPrice,
            String bidQuantity
    ) {
        return level(
                level,
                parseAbs(askPrice),
                parseAbs(askQuantity),
                parseAbs(bidPrice),
                parseAbs(bidQuantity)
        );
    }

    private static OrderBookLevelSnapshot level(
            int level,
            long askPrice,
            long askQuantity,
            long bidPrice,
            long bidQuantity
    ) {
        return new OrderBookLevelSnapshot(level, askPrice, askQuantity, bidPrice, bidQuantity);
    }

    static long parseAbs(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Math.abs(Long.parseLong(value.replace("+", "").replace(",", "").trim()));
    }
}
