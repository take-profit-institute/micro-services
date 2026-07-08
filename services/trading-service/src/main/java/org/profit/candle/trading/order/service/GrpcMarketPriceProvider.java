package org.profit.candle.trading.order.service;

import io.grpc.Deadline;
import io.grpc.StatusRuntimeException;
import org.profit.candle.proto.market.v1.GetQuoteRequest;
import org.profit.candle.proto.market.v1.GetQuoteResponse;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.springframework.context.annotation.Primary;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 시장가 즉시 체결(EXE-001)에 쓰는 현재가를 market-service의
 * {@code MarketService.GetQuote} gRPC로 <b>실시간 직접 조회</b>한다.
 *
 * <p>기존 {@link CachedMarketPriceProvider}(Kafka PriceUpdated 이벤트 인메모리 캐시)는
 * 이벤트 파이프라인(market 발행 → Kafka → 소비)이 온전해야만 값이 차서, 파이프라인
 * 지연/장애 시 캐시가 비어 모든 시장가 주문이 실패했다. 체결 시점에 gRPC로 정확한
 * 현재가를 받아오는 편이 명확하고 신뢰성이 높다.</p>
 *
 * <p>market-service 미응답/실패는 {@link StatusRuntimeException}으로 오는데, 그대로
 * 전파하면 상류(BFF)에 전송계층 오류(io exception)로 보인다. 여기서
 * {@link OrderErrorCode#MARKET_PRICE_UNAVAILABLE}(→ gRPC UNAVAILABLE)로 명확히 매핑한다.</p>
 *
 * <p>채널은 {@code spring.grpc.client.channel.market-service.target}
 * ({@code MARKET_SERVICE_GRPC_ADDRESS})로 생성된다. 체결이 무한 대기하지 않도록
 * per-call deadline을 둔다.</p>
 */
@Primary
@Component
public class GrpcMarketPriceProvider implements MarketPriceProvider {

    private static final int DEADLINE_SECONDS = 3;

    private final MarketServiceGrpc.MarketServiceBlockingStub stub;

    public GrpcMarketPriceProvider(GrpcChannelFactory channelFactory) {
        this.stub = MarketServiceGrpc.newBlockingStub(
                channelFactory.createChannel("market-service"));
    }

    @Override
    public long getCurrentPriceKrw(String symbol) {
        try {
            GetQuoteResponse response = stub
                    .withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                    .getQuote(GetQuoteRequest.newBuilder().setSymbol(symbol).build());
            long price = response.getQuote().getPrice();
            if (price <= 0) {
                // 아직 체결가가 없는 종목(신규 상장 직후 등) — 시장가 체결 불가
                throw new OrderException(OrderErrorCode.MARKET_PRICE_UNAVAILABLE);
            }
            return price;
        } catch (StatusRuntimeException e) {
            throw new OrderException(OrderErrorCode.MARKET_PRICE_UNAVAILABLE, e);
        }
    }
}
