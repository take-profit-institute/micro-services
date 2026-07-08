package org.profit.candle.market.session;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Duration;

/**
 * 장 세션 런타임 상태.
 *
 * OPEN/CLOSED 및 연결창 판정은 (현재시각, 오늘 세션창)의 순수 함수이므로 배치가 아니라 여기서
 * 계산한다. 정규장(09:00~22:00 KST, 데모 연장) + 주말/휴장일 배제로 거래일을 판정한다. 휴장일은
 * {@link TradingCalendar}(권위 소스 = batch)가 소유한다.
 *
 * @see docs/REALTIME_QUOTE_PIPELINE.md
 */
@Component
public class MarketSession {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 0);
    // 데모/테스트 목적으로 정규장 마감을 22:00까지 연장한다(실 정규장은 15:30).
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(22, 0);

    // WS 연결창: 실제 정규장(15:30) 직전~직후. 시가 형성 첫 틱과 마감 막판 틱을 확보하려고 살짝
    // 넓게 둔다. 데모용 REGULAR_CLOSE(22:00)와는 분리한다 — 15:30 이후엔 키움 틱이 없어 연결을
    // 유지할 이유가 없다.
    private static final LocalTime CONNECT_FROM = LocalTime.of(8, 50);
    private static final LocalTime DISCONNECT_AFTER = LocalTime.of(15, 40);

    private final Clock clock;
    private final TradingCalendar tradingCalendar;

    public MarketSession(Clock clock, TradingCalendar tradingCalendar) {
        this.clock = clock;
        this.tradingCalendar = tradingCalendar;
    }

    /** 오늘 거래일(KST). 캘린더 연동 전까지는 달력상 날짜다. */
    public LocalDate tradingDate() {
        return now().toLocalDate();
    }

    /** 오늘(KST) 거래일 여부. 주말·휴장일을 배제한다. */
    public boolean isTradingDay() {
        return isTradingDay(tradingDate());
    }

    /** 주어진 날짜의 거래일 여부. 주말·휴장일을 배제한다. */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !tradingCalendar.isHoliday(date);
    }

    /** wishlist 입력 계약의 marketStatus 값. 정규장 시간이면 OPEN, 아니면 CLOSED. */
    public String status() {
        if (!isTradingDay()) {
            return "CLOSED";
        }
        LocalTime t = now().toLocalTime();
        boolean open = !t.isBefore(REGULAR_OPEN) && t.isBefore(REGULAR_CLOSE);
        return open ? "OPEN" : "CLOSED";
    }

    /** 다음 정규장 시작(거래일 09:00 KST)까지 남은 시간. 장중이면 0을 돌려준다. */
    public Duration durationUntilNextRegularOpen() {
        ZonedDateTime current = now();
        if ("OPEN".equals(status())) {
            return Duration.ZERO;
        }

        LocalDate candidate = current.toLocalDate();
        if (!current.toLocalTime().isBefore(REGULAR_OPEN)) {
            candidate = candidate.plusDays(1);
        }
        while (!isTradingDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        ZonedDateTime nextOpen = ZonedDateTime.of(candidate, REGULAR_OPEN, KST);
        return Duration.between(current, nextOpen);
    }

    /** WS를 붙여둬야 하는 시간대인가. 거래일의 연결창(08:50~15:40) 안이면 true. */
    public boolean withinConnectionWindow() {
        if (!isTradingDay()) {
            return false;
        }
        LocalTime t = now().toLocalTime();
        return !t.isBefore(CONNECT_FROM) && t.isBefore(DISCONNECT_AFTER);
    }

    /**
     * 키움 체결시각 필드(20, HHMMSS)를 오늘 거래일 KST 기준 Instant 로 변환한다.
     * 파싱 실패 시 수신 시각으로 대체한다.
     */
    public Instant timestampOf(String hhmmss) {
        if (hhmmss == null || hhmmss.length() < 6) {
            return clock.instant();
        }
        try {
            int hh = Integer.parseInt(hhmmss.substring(0, 2));
            int mm = Integer.parseInt(hhmmss.substring(2, 4));
            int ss = Integer.parseInt(hhmmss.substring(4, 6));
            return ZonedDateTime.of(tradingDate(), LocalTime.of(hh, mm, ss), KST).toInstant();
        } catch (RuntimeException e) {
            return clock.instant();
        }
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(KST));
    }
}
