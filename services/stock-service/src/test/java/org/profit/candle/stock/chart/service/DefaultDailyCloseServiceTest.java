package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.event.OutboxWriter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDailyCloseServiceTest {

    @Mock CandleReader candleReader;
    @Mock OutboxWriter outboxWriter;

    private final LocalDate tradeDate = LocalDate.of(2026, 7, 1);
    private final Instant openTime = tradeDate.atStartOfDay(ZoneOffset.UTC).toInstant();

    @Test
    void closeDaily_marksOpenCandlesClosedAndRecordsEvents() {
        CandleEntity open = openCandle("005930", 72000);
        when(candleReader.findOpenAt("1d", openTime)).thenReturn(List.of(open));
        DefaultDailyCloseService service = new DefaultDailyCloseService(candleReader, outboxWriter);

        int closed = service.closeDaily(tradeDate);

        assertThat(closed).isEqualTo(1);
        assertThat(open.closed()).isTrue();
        verify(outboxWriter).recordStockDailyClosed("005930", tradeDate, 72000L);
    }

    @Test
    void closeDaily_isNoOpWhenNothingOpen() {
        when(candleReader.findOpenAt("1d", openTime)).thenReturn(List.of());
        DefaultDailyCloseService service = new DefaultDailyCloseService(candleReader, outboxWriter);

        assertThat(service.closeDaily(tradeDate)).isZero();
        verifyNoInteractions(outboxWriter);
    }

    private static CandleEntity openCandle(String code, long close) {
        CandleEntity candle = new CandleEntity(code, "1d", Instant.parse("2026-07-01T00:00:00Z"));
        // 진행 중(미확정) 캔들: closed=false 로 세팅
        candle.applyPrices(close - 100, close + 100, close - 200, close, 1000, false, "KIWOOM");
        return candle;
    }
}
