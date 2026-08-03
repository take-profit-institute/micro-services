package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.stock.chart.repository.CandleClose;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.chart.repository.CandleWriter;
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
    @Mock CandleWriter candleWriter;
    @Mock OutboxWriter outboxWriter;

    private final LocalDate tradeDate = LocalDate.of(2026, 7, 1);
    private final Instant openTime = tradeDate.atStartOfDay(ZoneOffset.UTC).toInstant();

    private DefaultDailyCloseService service() {
        return new DefaultDailyCloseService(candleReader, candleWriter, outboxWriter);
    }

    @Test
    void closeDaily_marksOpenCandlesClosedAndRecordsEvents() {
        when(candleReader.findClosesAt("1d", openTime)).thenReturn(List.of(close("005930", 72000)));
        when(outboxWriter.recordStockDailyClosed("005930", tradeDate, 72000L)).thenReturn(true);

        int recorded = service().closeDaily(tradeDate);

        assertThat(recorded).isEqualTo(1);
        verify(candleWriter).markClosedAt("1d", openTime);
        verify(outboxWriter).recordStockDailyClosed("005930", tradeDate, 72000L);
    }

    /**
     * 적재 배치가 정규장 마감(15:30 KST) 이후에 돌면 캔들이 이미 closed=true 로 들어온다.
     * 발행 대상을 closed=false 로 좁히면 이 경우 이벤트가 하나도 나가지 않는다.
     */
    @Test
    void closeDaily_recordsEventsEvenWhenCandlesArrivedAlreadyClosed() {
        when(candleReader.findClosesAt("1d", openTime)).thenReturn(List.of(
                close("005930", 72000),
                close("000660", 180000)
        ));
        when(candleWriter.markClosedAt("1d", openTime)).thenReturn(0); // 마감할 미확정 캔들이 없음
        when(outboxWriter.recordStockDailyClosed("005930", tradeDate, 72000L)).thenReturn(true);
        when(outboxWriter.recordStockDailyClosed("000660", tradeDate, 180000L)).thenReturn(true);

        assertThat(service().closeDaily(tradeDate)).isEqualTo(2);
    }

    @Test
    void closeDaily_doesNotCountAlreadyRecordedEvents() {
        when(candleReader.findClosesAt("1d", openTime)).thenReturn(List.of(
                close("005930", 72000),
                close("000660", 180000)
        ));
        when(outboxWriter.recordStockDailyClosed("005930", tradeDate, 72000L)).thenReturn(false);
        when(outboxWriter.recordStockDailyClosed("000660", tradeDate, 180000L)).thenReturn(true);

        // 재실행 시 이미 기록된 종목은 세지 않는다(멱등).
        assertThat(service().closeDaily(tradeDate)).isEqualTo(1);
    }

    @Test
    void closeDaily_isNoOpWhenNoCandlesForTradeDate() {
        when(candleReader.findClosesAt("1d", openTime)).thenReturn(List.of());

        assertThat(service().closeDaily(tradeDate)).isZero();
        verifyNoInteractions(outboxWriter);
    }

    private static CandleClose close(String stockCode, long close) {
        return new CandleClose(stockCode, close);
    }
}
