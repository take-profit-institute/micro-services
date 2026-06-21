package org.profit.candle.trading.grpc;

import io.grpc.stub.StreamObserver;
import org.profit.candle.proto.trading.v1.AccountBalance;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.profit.candle.proto.trading.v1.GetBalanceResponse;
import org.profit.candle.proto.trading.v1.TradingServiceGrpc;
import org.springframework.stereotype.Component;

/**
 * TradingService gRPC 엔드포인트.
 *
 * 스택 검증용 최소 구현 — BindableService 빈은 Spring gRPC 서버가 자동 등록한다.
 * 멱등성 인터셉터·도메인 로직은 이후 단계(task #2/#3)에서 채운다.
 */
@Component
public class TradingGrpcService extends TradingServiceGrpc.TradingServiceImplBase {

    @Override
    public void getBalance(GetBalanceRequest request, StreamObserver<GetBalanceResponse> responseObserver) {
        GetBalanceResponse response = GetBalanceResponse.newBuilder()
                .setBalance(AccountBalance.newBuilder()
                        .setUserId(request.getUserId())
                        .build())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
