package org.profit.candle.market.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.market.dto.IntradayTickResult;
import org.profit.candle.market.exception.MarketException;
import org.profit.candle.market.service.MarketIntradayService;
import org.profit.candle.proto.market.v1.GetIntradayTicksRequest;
import org.profit.candle.proto.market.v1.GetIntradayTicksResponse;
import org.profit.candle.proto.market.v1.IntradayTick;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * market-service gRPC 엔드포인트.
 *
 * 현재는 당일 틱 스냅샷(GetIntradayTicks)만 구현한다 — 실시간 그래프의 초기 페인트용.
 * SearchStocks/GetQuote/BatchQuotes 는 아직 미구현(호출 시 UNIMPLEMENTED).
 */
@Component
@RequiredArgsConstructor
public class MarketGrpcService extends MarketServiceGrpc.MarketServiceImplBase {

    private final MarketIntradayService intradayService;

    @Override
    public void getIntradayTicks(GetIntradayTicksRequest request,
            StreamObserver<GetIntradayTicksResponse> observer) {
        try {
            List<IntradayTickResult> ticks = intradayService.getIntradayTicks(request.getSymbol(), request.getLimit());

            GetIntradayTicksResponse.Builder builder = GetIntradayTicksResponse.newBuilder()
                    .setSymbol(request.getSymbol());
            for (IntradayTickResult t : ticks) {
                builder.addTicks(IntradayTick.newBuilder()
                        .setPrice(t.price())
                        .setTs(toTimestamp(t.time()))
                        .build());
            }
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (MarketException e) {
            // 키움 조회 실패 등 상류 장애 → UNAVAILABLE (BFF가 폴백 가능)
            observer.onError(Status.UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL.withDescription("INTERNAL").asRuntimeException());
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
