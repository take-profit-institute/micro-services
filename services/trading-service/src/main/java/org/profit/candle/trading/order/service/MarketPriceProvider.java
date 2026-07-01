package org.profit.candle.trading.order.service;

/**
 * 시장가 체결(EXE-001), 지정가 조건 체결(EXE-002) 판단에 쓰이는 현재가 조회.
 *
 * <p>Market-service는 시세 변경을 Kafka 이벤트(PriceUpdated)로 발행하고,
 * order_svc는 이를 구독해 로컬 캐시에 최신가를 유지한다 — 체결 시점에는
 * 캐시를 읽기만 하고 Market-service에 동기 호출하지 않는다. Market-service의
 * Redis Pub/Sub 채널(BFF→브라우저 WebSocket 전용)과는 별개 경로다.
 * order_svc가 Redis/TimescaleDB를 직접 찌르지 않는다 — 서비스 소유권 표상
 * OrderService는 "현재가 원천을 소유하지 않음"으로 명시되어 있다.</p>
 */
public interface MarketPriceProvider {

    /** symbol의 현재가(원 단위)를 반환한다. 조회 실패 시 OrderException을 던진다. */
    long getCurrentPriceKrw(String symbol);
}
