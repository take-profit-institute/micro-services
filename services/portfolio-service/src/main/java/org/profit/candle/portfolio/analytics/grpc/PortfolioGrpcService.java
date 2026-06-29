package org.profit.candle.portfolio.analytics.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.service.PortfolioAnalyticsService;
import org.profit.candle.proto.portfolio.v1.GetPortfolioHistoryRequest;
import org.profit.candle.proto.portfolio.v1.GetPortfolioHistoryResponse;
import org.profit.candle.proto.portfolio.v1.GetPortfolioSummaryRequest;
import org.profit.candle.proto.portfolio.v1.GetPortfolioSummaryResponse;
import org.profit.candle.proto.portfolio.v1.GetSectorBreakdownRequest;
import org.profit.candle.proto.portfolio.v1.GetSectorBreakdownResponse;
import org.profit.candle.proto.portfolio.v1.PortfolioServiceGrpc;
import org.profit.candle.proto.portfolio.v1.PortfolioSnapshot;
import org.profit.candle.proto.portfolio.v1.PortfolioSummary;
import org.profit.candle.proto.portfolio.v1.SectorWeight;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortfolioGrpcService extends PortfolioServiceGrpc.PortfolioServiceImplBase {

    private final PortfolioAnalyticsService analyticsService;

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
}
