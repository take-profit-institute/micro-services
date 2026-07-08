package org.profit.candle.portfolio.analytics.trading;

import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.proto.trading.v1.AccountServiceGrpc;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GrpcTradingBalanceClient implements TradingBalanceClient {
    private static final int DEADLINE_SECONDS = 3;
    private static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    private final AccountServiceGrpc.AccountServiceBlockingStub stub;

    public GrpcTradingBalanceClient(GrpcChannelFactory channelFactory) {
        this.stub = AccountServiceGrpc.newBlockingStub(
                channelFactory.createChannel("trading-service"));
    }

    @Override
    public long cash(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }

        Metadata metadata = new Metadata();
        metadata.put(USER_ID, userId);
        try {
            return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                    .getBalance(GetBalanceRequest.newBuilder()
                            .setUserId(userId)
                            .build())
                    .getBalance()
                    .getCash();
        } catch (StatusRuntimeException e) {
            log.warn("Trading GetBalance failed. userId={}", userId, e);
            return 0L;
        }
    }
}
