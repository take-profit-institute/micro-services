package org.profit.candle.market.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.market.dto.IntradayTickResult;
import org.profit.candle.market.exception.MarketException;
import org.profit.candle.market.service.MarketIntradayService;
import org.profit.candle.market.stream.QuoteStreamBroker;
import org.profit.candle.proto.market.v1.GetIntradayTicksRequest;
import org.profit.candle.proto.market.v1.GetIntradayTicksResponse;
import org.profit.candle.proto.market.v1.IntradayTick;
import org.profit.candle.proto.market.v1.LiveQuote;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.profit.candle.proto.market.v1.StreamQuotesRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * market-service gRPC 엔드포인트.
 *
 * GetIntradayTicks: 실시간 그래프의 초기 페인트용 당일 틱 스냅샷.
 * StreamQuotes: 종목 상세 뷰어에게 라이브 tick 팬아웃(구독 수요 획득/해제 포함).
 * SearchStocks/GetQuote/BatchQuotes 는 아직 미구현(호출 시 UNIMPLEMENTED).
 */
@Component
@RequiredArgsConstructor
public class MarketGrpcService extends MarketServiceGrpc.MarketServiceImplBase {

    private final MarketIntradayService intradayService;
    private final QuoteStreamBroker quoteStreamBroker;

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

    @Override
    public void streamQuotes(StreamQuotesRequest request, StreamObserver<LiveQuote> responseObserver) {
        // 모델 B(멀티플렉스): 심볼당 스트림 1개. 브라우저 뷰어 ref-count 는 BFF 가 하고,
        // 여기는 심볼 단위 upstream 수요만 다룬다.
        String symbol = request.getSymbol();
        if (symbol.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("symbol required").asRuntimeException());
            return;
        }
        // 서버 스트리밍 — 클라이언트 취소로 종료된다. 브로커가 팬아웃/수요 해제를 관리한다.
        ServerCallStreamObserver<LiveQuote> serverObserver =
                (ServerCallStreamObserver<LiveQuote>) responseObserver;
        quoteStreamBroker.subscribe(List.of(symbol), new ServerCallLiveQuoteStream(serverObserver));
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
