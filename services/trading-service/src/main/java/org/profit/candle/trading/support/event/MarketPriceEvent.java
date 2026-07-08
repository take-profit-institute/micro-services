package org.profit.candle.trading.support.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Market 도메인에서 발행하는 현재가 Kafka 이벤트 페이로드.
 * order/reservation 두 도메인이 공유하므로 trading.support.event 패키지에 둔다.
 *
 * <p>토픽명은 {@code market.price.v1}. orderbook 스냅샷 토픽과 섞지 않는다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketPriceEvent(String symbol, long price) {}
