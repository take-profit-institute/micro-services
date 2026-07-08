package org.profit.candle.market.orderbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.client.KiwoomMarketClient;
import org.profit.candle.market.entity.Stock;
import org.profit.candle.market.repository.StockRepository;
import org.profit.candle.market.session.MarketSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookRefreshService {
    private final StockRepository stockRepository;
    private final KiwoomMarketClient kiwoomMarketClient;
    private final OrderBookPublisher orderBookPublisher;
    private final OrderBookCacheService cacheService;
    private final MarketSession marketSession;

    public OrderBookRefreshResult refreshAllActiveStocks() {
        if (!"OPEN".equals(marketSession.status())) {
            return OrderBookRefreshResult.skipped("MARKET_CLOSED");
        }

        List<Stock> stocks = stockRepository.findByDeletedAtIsNullOrderByCodeAsc();
        int successCount = 0;
        int failCount = 0;
        for (Stock stock : stocks) {
            try {
                orderBookPublisher.publish(OrderBookMapper.toSnapshot(
                        stock.code(),
                        kiwoomMarketClient.getOrderBook(stock.code())
                ));
                successCount++;
            } catch (RuntimeException e) {
                failCount++;
                log.warn("OrderBook refresh failed. symbol={}", stock.code(), e);
            }
        }
        return new OrderBookRefreshResult(stocks.size(), successCount, failCount, false, "");
    }

    public Optional<OrderBookSnapshot> find(String symbol) {
        return cacheService.find(symbol);
    }
}
