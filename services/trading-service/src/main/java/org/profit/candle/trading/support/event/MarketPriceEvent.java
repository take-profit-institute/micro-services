package org.profit.candle.trading.support.event;

/**
 * Market 도메인에서 발행하는 현재가 Kafka 이벤트 페이로드.
 * order/reservation 두 도메인이 공유하므로 trading.support.event 패키지에 둔다.
 *
 * <p>토픽명은 Market 담당자(팀장)와 미확정 — 각 컨슈머의 TOPIC 상수 교체만으로 반영 가능.</p>
 */
public record MarketPriceEvent(String symbol, long price) {}