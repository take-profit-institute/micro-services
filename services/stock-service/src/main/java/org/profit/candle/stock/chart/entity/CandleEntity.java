package org.profit.candle.stock.chart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Entity
@Table(name = "candles")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandleEntity {

    @EmbeddedId
    private CandleId id;

    @Column(nullable = false)
    private long open;

    @Column(nullable = false)
    private long high;

    @Column(nullable = false)
    private long low;

    @Column(nullable = false)
    private long close;

    @Column(nullable = false)
    private long volume;

    @Column(nullable = false)
    private boolean closed;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public CandleEntity(String stockCode, String interval, Instant openTime) {
        this.id = new CandleId(stockCode, interval, openTime);
        this.closed = true;
        this.source = "KIWOOM";
    }

    public void applyPrices(long open, long high, long low, long close, long volume, boolean closed, String source) {
        if (open < 0 || high < 0 || low < 0 || close < 0 || volume < 0) {
            throw new IllegalArgumentException("캔들 가격과 거래량은 음수일 수 없습니다");
        }
        if (high < low) {
            throw new IllegalArgumentException("고가는 저가보다 작을 수 없습니다");
        }
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.closed = closed;
        this.source = source;
    }
}
