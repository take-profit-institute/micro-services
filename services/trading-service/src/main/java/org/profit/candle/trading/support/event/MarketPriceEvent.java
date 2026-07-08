package org.profit.candle.trading.support.event;

/**
 * Market 도메인에서 발행하는 현재가 Kafka 이벤트 페이로드.
 * order/reservation 두 도메인이 공유하므로 trading.support.event 패키지에 둔다.
 *
 * <p>토픽명은 Market 담당자(팀장)와 미확정 — 각 컨슈머의 TOPIC 상수 교체만으로 반영 가능.</p>
 *
 * <p><b>[2026-07-08 현황]</b> market-service가 현재 Kafka를 발행하지 않아 이 record는
 * 지금 당장은 실사용되지 않는다({@code OrderMarketPriceConsumer}/
 * {@code ReservationMarketPriceConsumer} 참고). 실시간 현재가는 대신
 * {@link MarketQuoteTick}(Redis Pub/Sub {@code market:quotes} 채널) 경로로 처리 중이다.
 * 삭제하지 않고 유지 — market-service가 추후 Kafka 발행을 추가하면 그대로 재사용한다.</p>
 */
public record MarketPriceEvent(String symbol, long price) {}