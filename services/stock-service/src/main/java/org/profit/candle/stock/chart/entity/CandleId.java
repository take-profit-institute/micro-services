package org.profit.candle.stock.chart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.Instant;

@Embeddable
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandleId implements Serializable {

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "interval", nullable = false, length = 4)
    private String interval;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    public CandleId(String stockCode, String interval, Instant openTime) {
        this.stockCode = stockCode;
        this.interval = interval;
        this.openTime = openTime;
    }
}
