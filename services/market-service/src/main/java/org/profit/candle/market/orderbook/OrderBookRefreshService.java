package org.profit.candle.market.orderbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.client.KiwoomMarketClient;
import org.profit.candle.market.publisher.MarketPriceEventPublisher;
import org.profit.candle.market.session.MarketSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookRefreshService {
    private final StockCatalogClient stockCatalogClient;
    private final KiwoomMarketClient kiwoomMarketClient;
    private final OrderBookPublisher orderBookPublisher;
    private final MarketPriceEventPublisher marketPriceEventPublisher;
    private final OrderBookCacheService cacheService;
    private final MarketSession marketSession;

    public OrderBookRefreshResult refreshAllActiveStocks() {
        if (!"OPEN".equals(marketSession.status())) {
            return OrderBookRefreshResult.skipped("MARKET_CLOSED");
        }

        List<String> stockCodes = stockCatalogClient.listListedStockCodes();
        int successCount = 0;
        int failCount = 0;
        for (String stockCode : stockCodes) {
            try {
                OrderBookSnapshot snapshot = OrderBookMapper.toSnapshot(
                        stockCode,
                        kiwoomMarketClient.getOrderBook(stockCode)
                );
                orderBookPublisher.publish(snapshot);
                marketPriceEventPublisher.publish(stockCode, priceOf(snapshot));
                successCount++;
            } catch (RuntimeException e) {
                failCount++;
                log.warn("OrderBook refresh failed. symbol={}", stockCode, e);
            }
        }
        return new OrderBookRefreshResult(stockCodes.size(), successCount, failCount, false, "");
    }

    public Optional<OrderBookSnapshot> find(String symbol) {
        return cacheService.find(symbol);
    }

    private long priceOf(OrderBookSnapshot snapshot) {
        if (snapshot.bestBidPrice() > 0) {
            return snapshot.bestBidPrice();
        }
        return snapshot.bestAskPrice();
    }
}
