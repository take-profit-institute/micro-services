package org.profit.candle.trading.order.service;


import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka로 수신한 Market PriceUpdated 이벤트를 보관하는 인메모리 캐시 기반 구현체.
 *
 * <p>갱신은 {@link #updatePrice}를 통해서만 일어난다 — 이 메서드는 Kafka
 * 컨슈머(별도 클래스, Market 이벤트 구독 설정과 함께 추가 예정)가 호출한다.
 * 이 클래스 자체는 Kafka 의존성을 모른다 — 컨슈머가 역직렬화한 symbol/price만
 * 받아 캐시에 반영하는 책임만 진다.</p>
 *
 * <p><b>한계</b>: 인메모리라 trading-service가 여러 인스턴스로 떠 있으면
 * 인스턴스마다 캐시가 따로 논다. 단일 인스턴스 운영(현재 프로젝트 규모)에서는
 * 문제없으나, 다중 인스턴스로 가면 Redis 등 공유 캐시로 교체해야 한다.</p>
 */
@Component
public class CachedMarketPriceProvider implements MarketPriceProvider{

    private final Map<String, Long> priceCache = new ConcurrentHashMap<>();

    @Override
    public long getCurrentPriceKrw(String symbol) {
        Long price = priceCache.get(symbol);
        if (price == null) {
            // 아직 PriceUpdated 이벤트를 한 번도 못 받은 종목 — 신규 상장 직후거나
            // Kafka 컨슈머가 아직 캐치업 중인 상태일 수 있다.
            throw new OrderException(OrderErrorCode.MARKET_PRICE_UNAVAILABLE);
        }
        return price;
    }

    /** Market 이벤트 컨슈머가 PriceUpdated 수신 시 호출. */
    public void updatePrice(String symbol, long priceKrw) {
        priceCache.put(symbol, priceKrw);
    }
}
