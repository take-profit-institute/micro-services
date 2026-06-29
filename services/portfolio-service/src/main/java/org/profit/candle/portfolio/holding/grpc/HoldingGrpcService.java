package org.profit.candle.portfolio.holding.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.portfolio.holding.dto.HoldingResult;
import org.profit.candle.portfolio.holding.exception.HoldingErrorCode;
import org.profit.candle.portfolio.holding.service.HoldingService;
import org.profit.candle.proto.portfolio.v1.GetHoldingRequest;
import org.profit.candle.proto.portfolio.v1.GetHoldingResponse;
import org.profit.candle.proto.portfolio.v1.Holding;
import org.profit.candle.proto.portfolio.v1.HoldingServiceGrpc;
import org.profit.candle.proto.portfolio.v1.ListHoldingsRequest;
import org.profit.candle.proto.portfolio.v1.ListHoldingsResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HoldingGrpcService extends HoldingServiceGrpc.HoldingServiceImplBase {

    private final HoldingService holdingService;

    @Override
    public void listHoldings(ListHoldingsRequest request, StreamObserver<ListHoldingsResponse> observer) {
        try {
            List<HoldingResult> results = holdingService.listHoldings(
                    request.getUserId(), request.getIncludeInactive());

            long totalBookValue = results.stream().mapToLong(HoldingResult::bookValue).sum();
            long totalRealizedProfit = results.stream().mapToLong(HoldingResult::realizedProfit).sum();

            observer.onNext(ListHoldingsResponse.newBuilder()
                    .addAllHoldings(results.stream().map(this::toProto).toList())
                    .setTotalBookValue(totalBookValue)
                    .setTotalRealizedProfit(totalRealizedProfit)
                    .build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void getHolding(GetHoldingRequest request, StreamObserver<GetHoldingResponse> observer) {
        try {
            HoldingResult result = holdingService.getHolding(request.getUserId(), request.getSymbol());
            observer.onNext(GetHoldingResponse.newBuilder().setHolding(toProto(result)).build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    private Holding toProto(HoldingResult r) {
        return Holding.newBuilder()
                .setSymbol(r.symbol())
                .setName(r.name())
                .setQuantity(r.quantity())
                .setAveragePrice(r.averagePrice())
                .setBookValue(r.bookValue())
                .setRealizedProfit(r.realizedProfit())
                .setActive(r.active())
                .setSector(r.sector())
                .setMarket(r.market())
                .build();
    }

    private Status toGrpcStatus(CandleException e) {
        if (e.errorCode() instanceof HoldingErrorCode code) {
            return switch (code) {
                case HOLDING_NOT_FOUND -> Status.NOT_FOUND.withDescription(code.code());
            };
        }
        return Status.INTERNAL.withDescription(e.getMessage());
    }
}
