package org.profit.candle.portfolio.analytics.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.RecordDailySnapshotCommand;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.dto.TradingStatsResult;
import org.profit.candle.portfolio.analytics.service.PortfolioAnalyticsService;
import org.profit.candle.portfolio.analytics.service.PortfolioSnapshotService;
import org.profit.candle.proto.common.v1.PageResponse;
import org.profit.candle.proto.portfolio.v1.DailyPortfolioSnapshot;
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
import org.profit.candle.proto.portfolio.v1.MonthlyReturn;
import org.profit.candle.proto.portfolio.v1.PortfolioServiceGrpc;
import org.profit.candle.proto.portfolio.v1.PortfolioSnapshot;
import org.profit.candle.proto.portfolio.v1.PortfolioSummary;
import org.profit.candle.proto.portfolio.v1.RecordDailySnapshotRequest;
import org.profit.candle.proto.portfolio.v1.RecordDailySnapshotResponse;
import org.profit.candle.proto.portfolio.v1.SectorWeight;
import org.profit.candle.proto.portfolio.v1.TradingStats;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class PortfolioGrpcService extends PortfolioServiceGrpc.PortfolioServiceImplBase {

    private final PortfolioAnalyticsService analyticsService;
    private final PortfolioSnapshotService snapshotService;

    @Override
    public void getPortfolioSummary(GetPortfolioSummaryRequest request,
                                    StreamObserver<GetPortfolioSummaryResponse> observer) {
        try {
            PortfolioSummaryResult result = analyticsService.getSummary(request.getUserId());
            observer.onNext(GetPortfolioSummaryResponse.newBuilder()
                    .setSummary(PortfolioSummary.newBuilder()
                            .setUserId(result.userId())
                            .setTotalBookValue(result.totalBookValue())
                            .setTotalStockValue(result.totalStockValue())
                            .setTotalUnrealizedProfit(result.totalUnrealizedProfit())
                            .setTotalRealizedProfit(result.totalRealizedProfit())
                            .setTotalReturnRate(result.totalReturnRate())
                            .setDayReturnRate(result.dayReturnRate())
                            .setDayProfit(result.dayProfit())
                            .setHoldingCount(result.holdingCount())
                            .build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getPortfolioHistory(GetPortfolioHistoryRequest request,
                                    StreamObserver<GetPortfolioHistoryResponse> observer) {
        try {
            PortfolioHistoryResult result = analyticsService.getHistory(request.getUserId(), request.getDays());
            GetPortfolioHistoryResponse.Builder builder = GetPortfolioHistoryResponse.newBuilder()
                    .setPeriodReturnRate(result.periodReturnRate())
                    .setPeriodProfit(result.periodProfit());

            result.snapshots().forEach(s -> builder.addSnapshots(PortfolioSnapshot.newBuilder()
                    .setDate(s.date())
                    .setTotalAsset(s.totalAsset())
                    .setStockValue(s.stockValue())
                    .setDailyProfit(s.dailyProfit())
                    .setCumulativeReturnRate(s.cumulativeReturnRate())
                    .build()));

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getSectorBreakdown(GetSectorBreakdownRequest request,
                                   StreamObserver<GetSectorBreakdownResponse> observer) {
        try {
            GetSectorBreakdownResponse.Builder builder = GetSectorBreakdownResponse.newBuilder();
            analyticsService.getSectorBreakdown(request.getUserId()).forEach(r ->
                    builder.addSectors(SectorWeight.newBuilder()
                            .setSector(r.sector())
                            .setWeight(r.weight())
                            .setBookValue(r.bookValue())
                            .setCount(r.count())
                            .build()));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void recordDailySnapshot(RecordDailySnapshotRequest request,
                                    StreamObserver<RecordDailySnapshotResponse> observer) {
        try {
            PortfolioSnapshotResult result = snapshotService.recordDailySnapshot(
                    new RecordDailySnapshotCommand(
                            request.getUserId(),
                            LocalDate.parse(request.getSnapshotDate()),
                            request.getTotalAsset(),
                            request.getStockValue(),
                            request.getSeedCapital(),
                            request.getIdempotencyKey()));
            observer.onNext(RecordDailySnapshotResponse.newBuilder()
                    .setSnapshot(PortfolioSnapshot.newBuilder()
                            .setDate(result.date())
                            .setTotalAsset(result.totalAsset())
                            .setStockValue(result.stockValue())
                            .setDailyProfit(result.dailyProfit())
                            .setCumulativeReturnRate(result.cumulativeReturnRate())
                            .build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listDailyPortfolioSnapshots(ListDailyPortfolioSnapshotsRequest request,
                                            StreamObserver<ListDailyPortfolioSnapshotsResponse> observer) {
        try {
            var result = snapshotService.listDailySnapshots(
                    LocalDate.parse(request.getSnapshotDate()),
                    request.getPage().getPageSize(),
                    blankToNull(request.getPage().getPageToken()));

            ListDailyPortfolioSnapshotsResponse.Builder builder = ListDailyPortfolioSnapshotsResponse.newBuilder()
                    .setPage(PageResponse.newBuilder()
                            .setNextPageToken(result.nextPageToken())
                            .build());

            result.snapshots().forEach(s -> builder.addSnapshots(DailyPortfolioSnapshot.newBuilder()
                    .setUserId(s.userId())
                    .setTotalAsset(s.totalAsset())
                    .setCumulativeReturnRate(s.cumulativeReturnRate())
                    .build()));

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (DateTimeParseException | IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getTradingStats(GetTradingStatsRequest request,
                                StreamObserver<GetTradingStatsResponse> observer) {
        try {
            TradingStatsResult result = analyticsService.getTradingStats(request.getUserId());
            observer.onNext(GetTradingStatsResponse.newBuilder()
                    .setStats(TradingStats.newBuilder()
                            .setUserId(result.userId())
                            .setTradeCount(result.tradeCount())
                            .setWinCount(result.winCount())
                            .setLossCount(result.lossCount())
                            .setWinRate(result.winRate())
                            .setAvgHoldingDays(result.avgHoldingDays())
                            .setBestSymbol(result.bestSymbol())
                            .setBestProfit(result.bestProfit())
                            .setWorstSymbol(result.worstSymbol())
                            .setWorstProfit(result.worstProfit())
                            .build())
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getMonthlyReturns(GetMonthlyReturnsRequest request,
                                  StreamObserver<GetMonthlyReturnsResponse> observer) {
        try {
            GetMonthlyReturnsResponse.Builder builder = GetMonthlyReturnsResponse.newBuilder();
            analyticsService.getMonthlyReturns(request.getUserId(), request.getMonths()).forEach(r ->
                    builder.addReturns(MonthlyReturn.newBuilder()
                            .setMonth(r.month())
                            .setReturnRate(r.returnRate())
                            .setProfit(r.profit())
                            .build()));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
