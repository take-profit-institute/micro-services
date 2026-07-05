package org.profit.candle.trading.client;

import io.grpc.Deadline;
import org.profit.candle.proto.market.v1.GetMarketStatusRequest;
import org.profit.candle.proto.market.v1.GetMarketStatusResponse;
import org.profit.candle.proto.market.v1.IsTradingDayRequest;
import org.profit.candle.proto.market.v1.IsTradingDayResponse;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * {@link MarketSessionClient} gRPC 구현체.
 *
 * <p>spring-grpc의 {@link GrpcChannelFactory}를 통해 application.yml의
 * {@code spring.grpc.client.channels.market-service.address}로 채널을 생성한다.
 * 배포 시 환경변수 {@code MARKET_SERVICE_GRPC_ADDRESS}만 교체하면 된다.</p>
 *
 * <p>즉시 주문 경로에서 동기 호출되므로 per-call deadline을 둔다. market-service가
 * 응답하지 않으면 {@code StatusRuntimeException(UNAVAILABLE/DEADLINE_EXCEEDED)}가
 * 그대로 전파돼 BFF에서 503으로 매핑된다 — 장 상태를 확인하지 못한 채 주문을
 * 체결하지 않는다(fail-safe).</p>
 */
@Component
public class DefaultMarketSessionClient implements MarketSessionClient {

    private static final int DEADLINE_SECONDS = 3;

    private final MarketServiceGrpc.MarketServiceBlockingStub stub;

    public DefaultMarketSessionClient(GrpcChannelFactory channelFactory) {
        this.stub = MarketServiceGrpc.newBlockingStub(
                channelFactory.createChannel("market-service"));
    }

    @Override
    public boolean isMarketOpen() {
        GetMarketStatusResponse response = stub
                .withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                .getMarketStatus(GetMarketStatusRequest.getDefaultInstance());
        return response.getOpen();
    }

    @Override
    public boolean isTradingDay(LocalDate date) {
        IsTradingDayResponse response = stub
                .withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                .isTradingDay(IsTradingDayRequest.newBuilder()
                        .setDate(date.toString()) // ISO-8601 YYYY-MM-DD
                        .build());
        return response.getTradingDay();
    }
}
