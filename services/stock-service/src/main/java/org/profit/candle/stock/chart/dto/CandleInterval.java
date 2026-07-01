package org.profit.candle.stock.chart.dto;

public enum CandleInterval {
    DAY_1("1d"),
    WEEK_1("1w"),
    MONTH_1("1M");

    private final String storageValue;

    CandleInterval(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
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
