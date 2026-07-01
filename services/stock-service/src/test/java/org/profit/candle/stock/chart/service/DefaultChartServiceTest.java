package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.dto.SparklineResult;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.chart.repository.SparklinePoint;

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

        verify(backfillService).backfill("005930", CandleInterval.DAY_1, 2, null);
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

    @Test
    void getSparklines_groupsRowsByCodePreservingRequestOrderAndSkipsMissing() {
        // 쿼리는 종목별 open_time ASC 로 내려준다. 요청 순서는 [000660, 005930, 035720].
        when(candleReader.findRecentCloses(List.of("000660", "005930", "035720"), "1d", 2))
                .thenReturn(List.of(
                        point("005930", "2026-06-29T00:00:00Z", 71000),
                        point("005930", "2026-06-30T00:00:00Z", 72000),
                        point("000660", "2026-06-30T00:00:00Z", 200000)));
        DefaultChartService service = new DefaultChartService(candleReader, backfillService);

        List<SparklineResult> result = service.getSparklines(
                List.of("000660", "005930", "035720"), CandleInterval.DAY_1, 2);

        // 035720 은 데이터가 없어 생략, 나머지는 요청 순서 유지.
        assertThat(result).extracting(SparklineResult::code).containsExactly("000660", "005930");
        assertThat(result.get(1).closes()).containsExactly(71000L, 72000L); // 오래된 -> 최신
        assertThat(result.get(1).lastOpenTime()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
    }

    @Test
    void getPreviousClose_returnsLatestCandleBeforeDate() {
        Instant date = Instant.parse("2026-07-01T00:00:00Z");
        CandleEntity prev = candle("005930", "1d", "2026-06-30T00:00:00Z", 72000);
        when(candleReader.findLatest("005930", "1d", date, 1)).thenReturn(List.of(prev));
        DefaultChartService service = new DefaultChartService(candleReader, backfillService);

        CandleResult result = service.getPreviousClose("005930", date);

        assertThat(result.close()).isEqualTo(72000L);
        assertThat(result.openTime()).isEqualTo(prev.id().openTime());
    }

    @Test
    void getPreviousClose_backfillsWhenDbEmpty() {
        Instant date = Instant.parse("2026-07-01T00:00:00Z");
        CandleEntity prev = candle("005930", "1d", "2026-06-30T00:00:00Z", 72000);
        when(candleReader.findLatest("005930", "1d", date, 1))
                .thenReturn(List.of())
                .thenReturn(List.of(prev));
        DefaultChartService service = new DefaultChartService(candleReader, backfillService);

        CandleResult result = service.getPreviousClose("005930", date);

        verify(backfillService).backfill("005930", CandleInterval.DAY_1, 100, date);
        assertThat(result.close()).isEqualTo(72000L);
    }

    @Test
    void getSparklines_throwsInvalidWhenNoUsableCodes() {
        DefaultChartService service = new DefaultChartService(candleReader, backfillService);

        assertThatThrownBy(() -> service.getSparklines(List.of("  ", ""), CandleInterval.DAY_1, 10))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(ChartErrorCode.INVALID_CANDLE_REQUEST));
    }

    private CandleEntity candle(String code, String interval, String openTime, long close) {
        CandleEntity candle = new CandleEntity(code, interval, Instant.parse(openTime));
        candle.applyPrices(close - 100, close + 100, close - 200, close, 1000, true, "KIWOOM");
        return candle;
    }

    private static SparklinePoint point(String code, String openTime, long close) {
        return new SparklinePoint() {
            @Override public String getStockCode() { return code; }
            @Override public Instant getOpenTime() { return Instant.parse(openTime); }
            @Override public long getClose() { return close; }
        };
    }
}
