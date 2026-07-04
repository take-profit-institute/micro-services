package org.profit.candle.portfolio.analytics.grpc;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.portfolio.analytics.dto.DailyPortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.ListDailyPortfolioSnapshotsResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.MonthlyReturnResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.dto.TradingStatsResult;
import org.profit.candle.portfolio.analytics.service.PortfolioAnalyticsService;
import org.profit.candle.portfolio.analytics.service.PortfolioSnapshotService;
import org.profit.candle.proto.common.v1.PageRequest;
import org.profit.candle.proto.portfolio.v1.GetMonthlyReturnsRequest;
import org.profit.candle.proto.portfolio.v1.GetMonthlyReturnsResponse;
import org.profit.candle.proto.portfolio.v1.GetPortfolioHistoryRequest;
import org.profit.candle.proto.portfolio.v1.GetPortfolioHistoryResponse;
import org.profit.candle.proto.portfolio.v1.GetPortfolioSummaryRequest;
import org.profit.candle.proto.portfolio.v1.GetPortfolioSummaryResponse;
import org.profit.candle.proto.portfolio.v1.GetSectorBreakdownRequest;
import org.profit.candle.proto.portfolio.v1.GetSectorBreakdownResponse;
import org.profit.candle.proto.portfolio.v1.GetTradingStatsRequest;
import org.profit.candle.proto.portfolio.v1.GetTradingStatsResponse;
import org.profit.candle.proto.portfolio.v1.ListDailyPortfolioSnapshotsRequest;
import org.profit.candle.proto.portfolio.v1.ListDailyPortfolioSnapshotsResponse;
import org.profit.candle.proto.portfolio.v1.RecordDailySnapshotRequest;
import org.profit.candle.proto.portfolio.v1.RecordDailySnapshotResponse;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioGrpcServiceTest {

    @Mock PortfolioAnalyticsService analyticsService;
    @Mock PortfolioSnapshotService snapshotService;
    PortfolioGrpcService service;

    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        service = new PortfolioGrpcService(analyticsService, snapshotService);
    }

    // ─── getPortfolioSummary ─────────────────────────────────────────────────

    @Test
    void getPortfolioSummary_happyPath_mapsAllFields() {
        when(analyticsService.getSummary(USER_ID)).thenReturn(
                new PortfolioSummaryResult(USER_ID, 1_000_000, 1_100_000, 100_000, 50_000,
                        "10.00", "2.50", 25_000, 3));
        CapturingObserver<GetPortfolioSummaryResponse> observer = new CapturingObserver<>();

        service.getPortfolioSummary(
                GetPortfolioSummaryRequest.newBuilder().setUserId(USER_ID).build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        var summary = observer.value.getSummary();
        assertThat(summary.getUserId()).isEqualTo(USER_ID);
        assertThat(summary.getTotalBookValue()).isEqualTo(1_000_000);
        assertThat(summary.getTotalStockValue()).isEqualTo(1_100_000);
        assertThat(summary.getTotalUnrealizedProfit()).isEqualTo(100_000);
        assertThat(summary.getTotalRealizedProfit()).isEqualTo(50_000);
        assertThat(summary.getTotalReturnRate()).isEqualTo("10.00");
        assertThat(summary.getDayReturnRate()).isEqualTo("2.50");
        assertThat(summary.getDayProfit()).isEqualTo(25_000);
        assertThat(summary.getHoldingCount()).isEqualTo(3);
    }

    @Test
    void getPortfolioSummary_emptyPortfolio_returnsZeroSummary() {
        when(analyticsService.getSummary(USER_ID)).thenReturn(
                new PortfolioSummaryResult(USER_ID, 0, 0, 0, 0, "0.00", "0.00", 0, 0));
        CapturingObserver<GetPortfolioSummaryResponse> observer = new CapturingObserver<>();

        service.getPortfolioSummary(
                GetPortfolioSummaryRequest.newBuilder().setUserId(USER_ID).build(), observer);

        assertThat(observer.value.getSummary().getHoldingCount()).isEqualTo(0);
        assertThat(observer.value.getSummary().getTotalReturnRate()).isEqualTo("0.00");
    }

    // ─── getPortfolioHistory ─────────────────────────────────────────────────

    @Test
    void getPortfolioHistory_happyPath_mapsSnapshotsAndPeriodStats() {
        List<PortfolioSnapshotResult> snapshots = List.of(
                new PortfolioSnapshotResult("2026-06-20", 1_000_000, 800_000, 0, "0.00"),
                new PortfolioSnapshotResult("2026-06-25", 1_050_000, 850_000, 10_000, "5.00"));
        when(analyticsService.getHistory(USER_ID, 7))
                .thenReturn(new PortfolioHistoryResult(snapshots, "5.00", 50_000));
        CapturingObserver<GetPortfolioHistoryResponse> observer = new CapturingObserver<>();

        service.getPortfolioHistory(
                GetPortfolioHistoryRequest.newBuilder().setUserId(USER_ID).setDays(7).build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.value.getSnapshotsList()).hasSize(2);
        assertThat(observer.value.getSnapshots(0).getDate()).isEqualTo("2026-06-20");
        assertThat(observer.value.getSnapshots(0).getTotalAsset()).isEqualTo(1_000_000);
        assertThat(observer.value.getSnapshots(1).getCumulativeReturnRate()).isEqualTo("5.00");
        assertThat(observer.value.getPeriodReturnRate()).isEqualTo("5.00");
        assertThat(observer.value.getPeriodProfit()).isEqualTo(50_000);
    }

    @Test
    void getPortfolioHistory_daysParameterPassedThrough() {
        when(analyticsService.getHistory(USER_ID, 30))
                .thenReturn(new PortfolioHistoryResult(List.of(), "0.00", 0));
        CapturingObserver<GetPortfolioHistoryResponse> observer = new CapturingObserver<>();

        service.getPortfolioHistory(
                GetPortfolioHistoryRequest.newBuilder().setUserId(USER_ID).setDays(30).build(), observer);

        org.mockito.Mockito.verify(analyticsService).getHistory(USER_ID, 30);
    }

    // ─── getSectorBreakdown ──────────────────────────────────────────────────

    @Test
    void getSectorBreakdown_happyPath_mapsSectorsInOrder() {
        when(analyticsService.getSectorBreakdown(USER_ID)).thenReturn(List.of(
                new SectorBreakdownResult("반도체", "66.67", 2_000_000, 2),
                new SectorBreakdownResult("인터넷", "33.33", 1_000_000, 1)));
        CapturingObserver<GetSectorBreakdownResponse> observer = new CapturingObserver<>();

        service.getSectorBreakdown(
                GetSectorBreakdownRequest.newBuilder().setUserId(USER_ID).build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.value.getSectorsList()).hasSize(2);
        assertThat(observer.value.getSectors(0).getSector()).isEqualTo("반도체");
        assertThat(observer.value.getSectors(0).getWeight()).isEqualTo("66.67");
        assertThat(observer.value.getSectors(0).getBookValue()).isEqualTo(2_000_000);
        assertThat(observer.value.getSectors(0).getCount()).isEqualTo(2);
    }

    @Test
    void getSectorBreakdown_noHoldings_returnsEmptyList() {
        when(analyticsService.getSectorBreakdown(USER_ID)).thenReturn(List.of());
        CapturingObserver<GetSectorBreakdownResponse> observer = new CapturingObserver<>();

        service.getSectorBreakdown(
                GetSectorBreakdownRequest.newBuilder().setUserId(USER_ID).build(), observer);

        assertThat(observer.value.getSectorsList()).isEmpty();
        assertThat(observer.completed).isTrue();
    }

    // ─── getTradingStats ─────────────────────────────────────────────────────

    @Test
    void getTradingStats_happyPath_mapsAllFields() {
        when(analyticsService.getTradingStats(USER_ID)).thenReturn(
                new TradingStatsResult(USER_ID, 10, 7, 3, "70.00", "5.30",
                        "005930", 50_000, "000660", -30_000));
        CapturingObserver<GetTradingStatsResponse> observer = new CapturingObserver<>();

        service.getTradingStats(
                GetTradingStatsRequest.newBuilder().setUserId(USER_ID).build(), observer);

        assertThat(observer.completed).isTrue();
        var stats = observer.value.getStats();
        assertThat(stats.getTradeCount()).isEqualTo(10);
        assertThat(stats.getWinCount()).isEqualTo(7);
        assertThat(stats.getWinRate()).isEqualTo("70.00");
        assertThat(stats.getAvgHoldingDays()).isEqualTo("5.30");
        assertThat(stats.getBestSymbol()).isEqualTo("005930");
        assertThat(stats.getWorstProfit()).isEqualTo(-30_000);
    }

    // ─── getMonthlyReturns ───────────────────────────────────────────────────

    @Test
    void getMonthlyReturns_happyPath_mapsReturnsInOrder() {
        when(analyticsService.getMonthlyReturns(USER_ID, 6)).thenReturn(List.of(
                new MonthlyReturnResult("2026-05", "10.00", 100_000),
                new MonthlyReturnResult("2026-06", "0.00", 0)));
        CapturingObserver<GetMonthlyReturnsResponse> observer = new CapturingObserver<>();

        service.getMonthlyReturns(
                GetMonthlyReturnsRequest.newBuilder().setUserId(USER_ID).setMonths(6).build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.value.getReturnsList()).hasSize(2);
        assertThat(observer.value.getReturns(0).getMonth()).isEqualTo("2026-05");
        assertThat(observer.value.getReturns(0).getReturnRate()).isEqualTo("10.00");
        assertThat(observer.value.getReturns(0).getProfit()).isEqualTo(100_000);
    }

    // ─── recordDailySnapshot ─────────────────────────────────────────────────

    @Test
    void recordDailySnapshot_happyPath_parsesDateAndMapsResult() {
        when(snapshotService.recordDailySnapshot(org.mockito.ArgumentMatchers.any())).thenReturn(
                new PortfolioSnapshotResult("2026-06-29", 1_100_000, 850_000, 50_000, "10.00"));
        CapturingObserver<RecordDailySnapshotResponse> observer = new CapturingObserver<>();

        service.recordDailySnapshot(RecordDailySnapshotRequest.newBuilder()
                .setUserId(USER_ID).setSnapshotDate("2026-06-29")
                .setTotalAsset(1_100_000).setStockValue(850_000)
                .setSeedCapital(1_000_000).setIdempotencyKey("idem-1").build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        var snapshot = observer.value.getSnapshot();
        assertThat(snapshot.getDate()).isEqualTo("2026-06-29");
        assertThat(snapshot.getDailyProfit()).isEqualTo(50_000);
        assertThat(snapshot.getCumulativeReturnRate()).isEqualTo("10.00");
    }

    @Test
    void recordDailySnapshot_invalidDate_emitsError() {
        CapturingObserver<RecordDailySnapshotResponse> observer = new CapturingObserver<>();

        service.recordDailySnapshot(RecordDailySnapshotRequest.newBuilder()
                .setUserId(USER_ID).setSnapshotDate("not-a-date").build(), observer);

        assertThat(observer.error).isNotNull();
        assertThat(observer.completed).isFalse();
    }

    // ─── listDailyPortfolioSnapshots ────────────────────────────────────────

    @Test
    void listDailyPortfolioSnapshots_happyPath_mapsRowsAndPageToken() {
        when(snapshotService.listDailySnapshots(LocalDate.of(2026, 6, 29), 500, "user-100"))
                .thenReturn(new ListDailyPortfolioSnapshotsResult(List.of(
                        new DailyPortfolioSnapshotResult("user-101", 1_100_000, "10.00"),
                        new DailyPortfolioSnapshotResult("user-102", 900_000, "-10.00")),
                        "user-102"));
        CapturingObserver<ListDailyPortfolioSnapshotsResponse> observer = new CapturingObserver<>();

        service.listDailyPortfolioSnapshots(ListDailyPortfolioSnapshotsRequest.newBuilder()
                .setSnapshotDate("2026-06-29")
                .setPage(PageRequest.newBuilder()
                        .setPageSize(500)
                        .setPageToken("user-100")
                        .build())
                .build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.value.getSnapshotsList()).hasSize(2);
        assertThat(observer.value.getSnapshots(0).getUserId()).isEqualTo("user-101");
        assertThat(observer.value.getSnapshots(0).getTotalAsset()).isEqualTo(1_100_000);
        assertThat(observer.value.getSnapshots(0).getCumulativeReturnRate()).isEqualTo("10.00");
        assertThat(observer.value.getPage().getNextPageToken()).isEqualTo("user-102");
    }

    @Test
    void listDailyPortfolioSnapshots_emptyResult_returnsEmptyList() {
        when(snapshotService.listDailySnapshots(LocalDate.of(2026, 6, 29), 0, null))
                .thenReturn(new ListDailyPortfolioSnapshotsResult(List.of(), ""));
        CapturingObserver<ListDailyPortfolioSnapshotsResponse> observer = new CapturingObserver<>();

        service.listDailyPortfolioSnapshots(ListDailyPortfolioSnapshotsRequest.newBuilder()
                .setSnapshotDate("2026-06-29")
                .build(), observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.value.getSnapshotsList()).isEmpty();
        assertThat(observer.value.getPage().getNextPageToken()).isEmpty();
        verify(snapshotService).listDailySnapshots(LocalDate.of(2026, 6, 29), 0, null);
    }

    @Test
    void listDailyPortfolioSnapshots_invalidDate_returnsInvalidArgument() {
        CapturingObserver<ListDailyPortfolioSnapshotsResponse> observer = new CapturingObserver<>();

        service.listDailyPortfolioSnapshots(ListDailyPortfolioSnapshotsRequest.newBuilder()
                .setSnapshotDate("not-a-date")
                .build(), observer);

        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(observer.completed).isFalse();
    }

    @Test
    void listDailyPortfolioSnapshots_invalidPageSize_returnsInvalidArgument() {
        when(snapshotService.listDailySnapshots(LocalDate.of(2026, 6, 29), 501, null))
                .thenThrow(new IllegalArgumentException("page_size must be between 1 and 500"));
        CapturingObserver<ListDailyPortfolioSnapshotsResponse> observer = new CapturingObserver<>();

        service.listDailyPortfolioSnapshots(ListDailyPortfolioSnapshotsRequest.newBuilder()
                .setSnapshotDate("2026-06-29")
                .setPage(PageRequest.newBuilder().setPageSize(501).build())
                .build(), observer);

        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(observer.completed).isFalse();
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    static class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override public void onNext(T v) { value = v; }
        @Override public void onError(Throwable t) { error = t; }
        @Override public void onCompleted() { completed = true; }
    }
}
