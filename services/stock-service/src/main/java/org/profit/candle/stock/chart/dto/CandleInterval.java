package org.profit.candle.stock.chart.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;

public enum CandleInterval {
    DAY_1("1d"),
    WEEK_1("1w"),
    MONTH_1("1M");

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** KRX 정규장 마감(15:30 KST). 이 시각을 지나면 당일 일봉을 확정으로 본다. */
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final String storageValue;

    CandleInterval(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    /**
     * {@code openTime} 캔들의 주기가 {@code now} 시점 기준으로 이미 끝났는지(확정 종가) 판정한다.
     * 진행 중인(현재 주기) 캔들은 false 로, 지난 주기는 true 로 본다. 모두 KST 달력 기준.
     */
    public boolean isPeriodClosed(Instant openTime, Instant now) {
        LocalDate candleDate = openTime.atZone(KST).toLocalDate();
        LocalDate today = now.atZone(KST).toLocalDate();
        return switch (this) {
            // 당일 캔들은 장 마감(15:30 KST) 이후 확정. 마감 후 재백필이 closed 를 되돌리지 않도록 시각까지 본다.
            case DAY_1 -> candleDate.isBefore(today)
                    || (candleDate.isEqual(today) && !now.atZone(KST).toLocalTime().isBefore(MARKET_CLOSE));
            case WEEK_1 -> weekStart(candleDate).isBefore(weekStart(today));
            case MONTH_1 -> YearMonth.from(candleDate).isBefore(YearMonth.from(today));
        };
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    public static CandleInterval fromStorageValue(String value) {
        for (CandleInterval interval : values()) {
            if (interval.storageValue.equals(value)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 캔들 주기입니다: " + value);
    }
}
