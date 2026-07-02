package org.profit.candle.batch.portfolio.eod.client;

import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.profit.candle.proto.portfolio.v1.PortfolioServiceGrpc;
import org.profit.candle.proto.portfolio.v1.RecordDailySnapshotRequest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-eod")
public class GrpcPortfolioSnapshotClient implements PortfolioSnapshotClient {

    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub stub;
    private final long writeDeadlineMillis;
    private final RequestIdGenerator requestIdGenerator;

    public GrpcPortfolioSnapshotClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties,
            RequestIdGenerator requestIdGenerator
    ) {
        this.stub = PortfolioServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().portfolioTarget())
        );
        this.writeDeadlineMillis = batchProperties.grpc().writeDeadlineMillis();
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    public void recordDailySnapshot(SnapshotCommand command) {
        try {
            stub.withInterceptors(GrpcClientSupport.userWrite(
                            command.userId(),
                            requestIdGenerator.generate(),
                            command.idempotencyKey()
                    ))
                    .withDeadlineAfter(writeDeadlineMillis, TimeUnit.MILLISECONDS)
                    .recordDailySnapshot(RecordDailySnapshotRequest.newBuilder()
                            .setUserId(command.userId())
                            .setSnapshotDate(command.businessDate().toString())
                            .setTotalAsset(command.totalAsset())
                            .setStockValue(command.stockValue())
                            .setSeedCapital(command.seedCapital())
                            .setIdempotencyKey(command.idempotencyKey())
                            .build());
        } catch (StatusRuntimeException exception) {
            throw GrpcClientSupport.mapException(exception);
        }
    }
}
