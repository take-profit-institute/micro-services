package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.repository.CandleReader;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultChartServiceTest {

    @Mock CandleReader candleReader;
    @Mock CandleBackfillService backfillService;

    @Test
    void getCandles_returnsAscendingCandlesFromDb() {
        CandleEntity latest = candle("005930", "1d", "2026-06-30T00:00:00Z", 72000);
        CandleEntity previous = candle("005930", "1d", "2026-06-29T00:00:00Z", 71000);
        when(candleReader.findLatest("005930", "1d", null, 2)).thenReturn(List.of(latest, previous));
        DefaultChartService service = new DefaultChartService(candleReader, backfillService);

        List<CandleResult> result = service.getCandles("005930", CandleInterval.DAY_1, 2, null);

        assertThat(result).extracting(CandleResult::openTime)
                .containsExactly(previous.id().openTime(), latest.id().openTime());
    }

    @Test
    void getCandles_backfillsWhenDbHasNotEnoughCandles() {
        CandleEntity candle = candle("005930", "1d", "2026-06-30T00:00:00Z", 72000);
        when(candleReader.findLatest("005930", "1d", null, 2))
                .thenReturn(List.of())
                .thenReturn(List.of(candle));
        DefaultChartService service = new DefaultChartService(candleReader, backfillService);

        List<CandleResult> result = service.getCandles("005930", CandleInterval.DAY_1, 2, null);

        verify(backfillService).backfill("005930", CandleInterval.DAY_1, 2);
        assertThat(result).hasSize(1);
    }

    @Test
    void getCandles_throwsUnavailableWhenFallbackStillEmpty() {
        when(candleReader.findLatest("005930", "1d", null, 1)).thenReturn(List.of());
        DefaultChartService service = new DefaultChartService(candleReader, backfillService);

        assertThatThrownBy(() -> service.getCandles("005930", CandleInterval.DAY_1, 1, null))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(ChartErrorCode.CHART_DATA_UNAVAILABLE));
    }

    private CandleEntity candle(String code, String interval, String openTime, long close) {
        CandleEntity candle = new CandleEntity(code, interval, Instant.parse(openTime));
        candle.applyPrices(close - 100, close + 100, close - 200, close, 1000, true, "KIWOOM");
        return candle;
    }
}
