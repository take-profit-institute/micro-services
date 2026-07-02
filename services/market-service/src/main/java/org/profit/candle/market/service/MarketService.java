package org.profit.candle.market.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.profit.candle.market.client.KiwoomMarketClient;
import org.profit.candle.market.dto.response.KiwoomStockResponse;
import org.profit.candle.market.entity.Stock;
import org.profit.candle.market.repository.StockRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final KiwoomMarketClient kiwoomMarketClient;
    private final StockRepository stockRepository;

    @Transactional
    public Stock syncStock(String stockCode) {
        KiwoomStockResponse response = kiwoomMarketClient.getStockInfo(stockCode);

        if (response.returnCode() != 0) {
            throw new IllegalStateException("키움 종목 조회 실패: " + response.returnMsg());
        }

        Stock stock = stockRepository.findByCodeAndDeletedAtIsNull(response.stockCode())
                .orElseGet(() -> new Stock(
                        response.stockCode(),
                        response.stockName(),
                        "KOSPI"
                ));
        return stockRepository.save(stock);
    }
}
