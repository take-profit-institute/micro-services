package org.profit.candle.trading.reservation.event;

/**
 * Market 도메인에서 발행하는 현재가 Kafka 이벤트 페이로드.
 *
 * <p>이벤트명/토픽명은 Market 담당자(팀장)와 미확정 — {@code MarketPriceConsumer}의
 * {@code TOPIC} 상수 교체만으로 반영 가능하다.</p>
 */
public record MarketPriceEvent(String symbol, long price) {}

