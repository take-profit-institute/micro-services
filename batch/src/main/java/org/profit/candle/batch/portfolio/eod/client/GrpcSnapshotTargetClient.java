package org.profit.candle.batch.portfolio.eod.client;

import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.proto.common.v1.PageRequest;
import org.profit.candle.proto.portfolio.v1.ActiveHolder;
import org.profit.candle.proto.portfolio.v1.HoldingServiceGrpc;
import org.profit.candle.proto.portfolio.v1.ListActiveHoldersRequest;
import org.profit.candle.proto.portfolio.v1.ListActiveHoldersResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

/** Portfolio의 활성 보유자를 유저 단위 cursor page로 조회한다. */
@Component
@Profile("!local-eod")
public class GrpcSnapshotTargetClient implements SnapshotTargetClient {

    private final HoldingServiceGrpc.HoldingServiceBlockingStub stub;
    private final long readDeadlineMillis;
    private final RequestIdGenerator requestIdGenerator;

    public GrpcSnapshotTargetClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties,
            RequestIdGenerator requestIdGenerator
    ) {
        this.stub = HoldingServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().portfolioTarget())
        );
        this.readDeadlineMillis = batchProperties.grpc().readDeadlineMillis();
        this.requestIdGenerator = requestIdGenerator;
    }

    /** 활성 수량이 있는 유저와 전체 보유 포지션 한 페이지를 조회한다. */
    @Override
    public SnapshotTarget.Page loadTargets(
            LocalDate businessDate,
            String pageToken,
            int pageSize
    ) {
        try {
            ListActiveHoldersResponse response = stub
                    .withInterceptors(GrpcClientSupport.systemRead(requestIdGenerator.generate()))
                    .withDeadlineAfter(readDeadlineMillis, TimeUnit.MILLISECONDS)
                    .listActiveHolders(ListActiveHoldersRequest.newBuilder()
                            .setPage(PageRequest.newBuilder()
                                    .setPageSize(pageSize)
                                    .setPageToken(pageToken)
                                    .build())
                            .build());
            List<SnapshotTarget> targets = response.getHoldersList().stream()
                    .map(this::toTarget)
                    .toList();
            return new SnapshotTarget.Page(targets, response.getPage().getNextPageToken());
        } catch (StatusRuntimeException exception) {
            throw GrpcClientSupport.mapException(exception);
        }
    }

    /** Portfolio proto 응답을 Batch 내부 모델로 변환한다. */
    private SnapshotTarget toTarget(ActiveHolder holder) {
        List<SnapshotTarget.Holding> holdings = holder.getPositionsList().stream()
                .map(position -> new SnapshotTarget.Holding(
                        position.getSymbol(),
                        position.getQuantity(),
                        position.getAveragePrice()
                ))
                .toList();
        return new SnapshotTarget(holder.getUserId(), holdings);
    }
}
