package org.profit.candle.stock.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StockDailyClosedEventTest {

    private final LocalDate tradeDate = LocalDate.of(2026, 7, 1);

    /** 결정적 id 가 outbox PK 충돌로 중복 발행을 막는 근거다. */
    @Test
    void eventId_isStableForSameStockAndTradeDate() {
        StockDailyClosedEvent first = StockDailyClosedEvent.create("005930", tradeDate, 72000, Instant.now());
        StockDailyClosedEvent second = StockDailyClosedEvent.create("005930", tradeDate, 72000, Instant.now());

        assertThat(first.eventId()).isEqualTo(second.eventId());
        assertThat(first.eventId()).isEqualTo(StockDailyClosedEvent.eventId("005930", tradeDate));
    }

    /** 종가가 정정돼도 "그 종목의 그날 종가"는 하나여야 하므로 id 는 그대로다. */
    @Test
    void eventId_ignoresClosePrice() {
        StockDailyClosedEvent first = StockDailyClosedEvent.create("005930", tradeDate, 72000, Instant.now());
        StockDailyClosedEvent corrected = StockDailyClosedEvent.create("005930", tradeDate, 71500, Instant.now());

        assertThat(first.eventId()).isEqualTo(corrected.eventId());
    }

    @Test
    void eventId_differsPerStockAndPerTradeDate() {
        StockDailyClosedEvent samsung = StockDailyClosedEvent.create("005930", tradeDate, 72000, Instant.now());
        StockDailyClosedEvent hynix = StockDailyClosedEvent.create("000660", tradeDate, 72000, Instant.now());
        StockDailyClosedEvent nextDay =
                StockDailyClosedEvent.create("005930", tradeDate.plusDays(1), 72000, Instant.now());

        assertThat(samsung.eventId()).isNotEqualTo(hynix.eventId());
        assertThat(samsung.eventId()).isNotEqualTo(nextDay.eventId());
    }
}
