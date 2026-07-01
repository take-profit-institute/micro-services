package org.profit.candle.batch.portfolio.eod.client;

import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.profit.candle.proto.trading.v1.GetBalanceResponse;
import org.profit.candle.proto.trading.v1.TradingServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-eod")
public class GrpcCashBalanceClient implements CashBalanceClient {

    private final TradingServiceGrpc.TradingServiceBlockingStub stub;
    private final long readDeadlineMillis;
    private final RequestIdGenerator requestIdGenerator;

    public GrpcCashBalanceClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties,
            RequestIdGenerator requestIdGenerator
    ) {
        this.stub = TradingServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().tradingTarget())
        );
        this.readDeadlineMillis = batchProperties.grpc().readDeadlineMillis();
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    public long getCash(String userId) {
        try {
            GetBalanceResponse response = stub
                    .withInterceptors(GrpcClientSupport.userRead(
                            userId,
                            requestIdGenerator.generate()
                    ))
                    .withDeadlineAfter(readDeadlineMillis, TimeUnit.MILLISECONDS)
                    .getBalance(GetBalanceRequest.newBuilder().setUserId(userId).build());

            if (!response.hasBalance()) {
                throw new EodBatchException(EodBatchErrorCode.TRADING_BALANCE_MISSING);
            }
            return response.getBalance().getCash();
        } catch (StatusRuntimeException exception) {
            throw GrpcClientSupport.mapException(exception);
        }
    }
}
