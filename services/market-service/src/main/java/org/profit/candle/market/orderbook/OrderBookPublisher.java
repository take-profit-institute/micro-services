package org.profit.candle.market.orderbook;

public interface OrderBookPublisher {

    void publish(OrderBookSnapshot snapshot);
}
