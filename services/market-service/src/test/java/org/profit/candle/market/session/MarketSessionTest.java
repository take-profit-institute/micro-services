package org.profit.candle.market.session;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarketSessionTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final TradingCalendar NO_HOLIDAYS = date -> false;

    private MarketSession at(String kstDateTime) {
        return at(kstDateTime, NO_HOLIDAYS);
    }

    private MarketSession at(String kstDateTime, TradingCalendar calendar) {
        // 주어진 KST 벽시계 시각을 고정한 Clock 으로 세션을 만든다
        Instant instant = java.time.LocalDateTime.parse(kstDateTime).atZone(KST).toInstant();
        return new MarketSession(Clock.fixed(instant, KST), calendar);
    }

    @Test
    void status_isOpen_duringRegularHoursOnWeekday() {
        assertThat(at("2026-07-03T10:47:09").status()).isEqualTo("OPEN"); // 금요일 정규장
    }

    @Test
    void status_isClosed_beforeOpenAndAfterClose() {
        assertThat(at("2026-07-03T08:59:59").status()).isEqualTo("CLOSED");
        assertThat(at("2026-07-03T22:00:00").status()).isEqualTo("CLOSED"); // 마감시각(22:00)은 이미 CLOSED
    }

    @Test
    void status_isClosed_onWeekend() {
        assertThat(at("2026-07-04T10:47:09").status()).isEqualTo("CLOSED"); // 토요일
        assertThat(at("2026-07-05T10:47:09").status()).isEqualTo("CLOSED"); // 일요일
    }

    @Test
    void connectionWindow_opensBeforeOpen_and_closesAfterClose() {
        assertThat(at("2026-07-03T08:49:59").withinConnectionWindow()).isFalse();
        assertThat(at("2026-07-03T08:50:00").withinConnectionWindow()).isTrue();  // 창 진입
        assertThat(at("2026-07-03T15:39:59").withinConnectionWindow()).isTrue();  // 마감 후에도 잠시 유지
        assertThat(at("2026-07-03T15:40:00").withinConnectionWindow()).isFalse(); // 창 이탈
    }

    @Test
    void connectionWindow_isClosed_onWeekend() {
        assertThat(at("2026-07-04T10:00:00").withinConnectionWindow()).isFalse();
    }

    @Test
    void status_isClosed_onHoliday_evenDuringRegularHoursOnWeekday() {
        TradingCalendar holiday = date -> date.equals(LocalDate.of(2026, 1, 1)); // 신정(목)
        assertThat(at("2026-01-01T10:47:09", holiday).status()).isEqualTo("CLOSED");
        assertThat(at("2026-01-01T10:00:00", holiday).withinConnectionWindow()).isFalse();
        assertThat(at("2026-01-01T10:00:00", holiday).isTradingDay()).isFalse();
    }

    @Test
    void durationUntilNextRegularOpen_skipsWeekendAndHolidays() {
        TradingCalendar holiday = date -> date.equals(LocalDate.of(2026, 7, 6)); // Monday holiday

        assertThat(at("2026-07-03T22:01:00", holiday).durationUntilNextRegularOpen())
                .isEqualTo(Duration.ofHours(82).plusMinutes(59));
    }

    @Test
    void durationUntilNextRegularOpen_isZeroDuringRegularHours() {
        assertThat(at("2026-07-03T10:00:00").durationUntilNextRegularOpen()).isZero();
    }

    @Test
    void isTradingDay_true_onNormalWeekdayWithNoHoliday() {
        assertThat(at("2026-07-03T10:00:00", Set.of()::contains).isTradingDay()).isTrue();
    }

    @Test
    void timestampOf_combinesKiwoomTimeWithTradingDate() {
        Instant expected = java.time.LocalDateTime.parse("2026-07-03T10:47:09").atZone(KST).toInstant();
        assertThat(at("2026-07-03T10:47:30").timestampOf("104709")).isEqualTo(expected);
    }

    @Test
    void timestampOf_fallsBackToNow_whenFieldMalformed() {
        MarketSession session = at("2026-07-03T10:47:30");
        Instant now = java.time.LocalDateTime.parse("2026-07-03T10:47:30").atZone(KST).toInstant();
        assertThat(session.timestampOf("")).isEqualTo(now);
        assertThat(session.timestampOf(null)).isEqualTo(now);
    }
}
