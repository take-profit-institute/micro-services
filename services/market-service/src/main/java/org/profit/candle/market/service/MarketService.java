package org.profit.candle.market.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.profit.candle.market.client.KiwoomMarketClient;
import org.profit.candle.market.dto.message.StockPriceMessage;
import org.profit.candle.market.dto.response.KiwoomStockResponse;
import org.profit.candle.market.entity.Stock;
import org.profit.candle.market.publisher.StockPricePublisher;
import org.profit.candle.market.repository.StockRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final KiwoomMarketClient kiwoomMarketClient;
//    private final StockRepository stockRepository;
    private final StockPricePublisher stockPricePublisher;

    public void publishStock(String stockCode) {
        KiwoomStockResponse response = kiwoomMarketClient.getStockInfo(stockCode);

        if(response.returnCode() != 0) {
            throw new IllegalArgumentException("키움 종목 조회 실패: " + response.returnMsg());
        }

        stockPricePublisher.publish(new StockPriceMessage(
                response.stockCode(),
                response.stockName(),
                response.getCurrentPriceValue(),
                response.getPriceChangeValue(),
                response.getPriceChangeRateValue(),
                response.getTradingVolumeValue()
        ));
    }
}
