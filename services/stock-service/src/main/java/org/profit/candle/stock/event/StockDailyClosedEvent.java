package org.profit.candle.stock.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 장 마감 후 확정된 일봉 종가 이벤트. 소비자(랭킹/포트폴리오/알림)는 {@code eventId} 로 중복을 무시한다.
 * topic {@code stock.daily-closed.v1}, Kafka key = stockCode.
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
                UUID.randomUUID(),
                StockEventType.STOCK_DAILY_CLOSED.wireName(),
                StockEventType.STOCK_DAILY_CLOSED.version(),
                stockCode,
                tradeDate,
                close,
                occurredAt);
    }
}
