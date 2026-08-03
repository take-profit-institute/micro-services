package org.profit.candle.stock.event;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 장 마감 후 확정된 일봉 종가 이벤트. 소비자(랭킹/포트폴리오/알림)는 {@code eventId} 로 중복을 무시한다.
 * topic {@code stock.daily-closed.v1}, Kafka key = stockCode.
 *
 * <p>{@code eventId} 는 (종목, 거래일)에서 결정적으로 파생된다. 같은 거래일을 몇 번 마감 처리하든
 * 같은 id 가 나오므로 outbox PK 충돌로 중복 발행이 걸러지고, 소비자의 eventId 기반 중복 제거도
 * 재실행 사이에서 실제로 동작한다. 종가는 식별자에 넣지 않는다 — "그 종목의 그날 종가"가 하나여야 한다.
 */
public record StockDailyClosedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        String stockCode,
        LocalDate tradeDate,
        long close,
        Instant occurredAt) {

    public static StockDailyClosedEvent create(String stockCode, LocalDate tradeDate, long close, Instant occurredAt) {
        return new StockDailyClosedEvent(
                eventId(stockCode, tradeDate),
                StockEventType.STOCK_DAILY_CLOSED.wireName(),
                StockEventType.STOCK_DAILY_CLOSED.version(),
                stockCode,
                tradeDate,
                close,
                occurredAt);
    }

    /** (이벤트 타입, 종목, 거래일)에서 파생한 결정적 id. 재실행해도 같은 값이 나온다. */
    public static UUID eventId(String stockCode, LocalDate tradeDate) {
        String name = StockEventType.STOCK_DAILY_CLOSED.wireName() + ":" + stockCode + ":" + tradeDate;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
