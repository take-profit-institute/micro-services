package org.profit.candle.stock.chart.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CandleIntervalTest {

    // KST = UTC+9. 15:30 KST = 06:30 UTC (장 마감 컷오프).
    private static final Instant TODAY_OPEN = Instant.parse("2026-07-01T00:00:00Z"); // 2026-07-01 KST
    private static final Instant BEFORE_CLOSE = Instant.parse("2026-07-01T05:00:00Z"); // 14:00 KST
    private static final Instant AFTER_CLOSE = Instant.parse("2026-07-01T07:00:00Z");  // 16:00 KST

    @Test
    void day1_todayCandleIsOpenBeforeCloseAndClosedAfter() {
        assertThat(CandleInterval.DAY_1.isPeriodClosed(TODAY_OPEN, BEFORE_CLOSE)).isFalse();
        assertThat(CandleInterval.DAY_1.isPeriodClosed(TODAY_OPEN, AFTER_CLOSE)).isTrue();
    }

    @Test
    void day1_pastCandleIsAlwaysClosed() {
        Instant yesterday = Instant.parse("2026-06-30T00:00:00Z");
        assertThat(CandleInterval.DAY_1.isPeriodClosed(yesterday, BEFORE_CLOSE)).isTrue();
    }

    @Test
    void week1_currentWeekOpenPastWeekClosed() {
        // 2026-07-01 은 수요일. 같은 주 월요일(06-29) 캔들은 진행 중, 지난 주(06-22)는 마감.
        Instant sameWeek = Instant.parse("2026-06-29T00:00:00Z");
        Instant lastWeek = Instant.parse("2026-06-22T00:00:00Z");
        assertThat(CandleInterval.WEEK_1.isPeriodClosed(sameWeek, BEFORE_CLOSE)).isFalse();
        assertThat(CandleInterval.WEEK_1.isPeriodClosed(lastWeek, BEFORE_CLOSE)).isTrue();
    }

    @Test
    void month1_currentMonthOpenPastMonthClosed() {
        Instant thisMonth = Instant.parse("2026-07-01T00:00:00Z");
        Instant lastMonth = Instant.parse("2026-06-01T00:00:00Z");
        assertThat(CandleInterval.MONTH_1.isPeriodClosed(thisMonth, AFTER_CLOSE)).isFalse();
        assertThat(CandleInterval.MONTH_1.isPeriodClosed(lastMonth, AFTER_CLOSE)).isTrue();
    }
}
