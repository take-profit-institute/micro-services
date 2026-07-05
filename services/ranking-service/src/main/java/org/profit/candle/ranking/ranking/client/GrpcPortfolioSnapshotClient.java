package org.profit.candle.ranking.ranking.client;

import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.profit.candle.proto.common.v1.PageRequest;
import org.profit.candle.proto.portfolio.v1.ListDailyPortfolioSnapshotsRequest;
import org.profit.candle.proto.portfolio.v1.PortfolioServiceGrpc;
import org.profit.candle.ranking.config.RankingGrpcProperties;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcPortfolioSnapshotClient implements PortfolioSnapshotClient {

    private static final String PORTFOLIO_CHANNEL = "portfolio";

    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcPortfolioSnapshotClient(
            GrpcChannelFactory channelFactory,
            RankingGrpcProperties properties) {
        this.stub = PortfolioServiceGrpc.newBlockingStub(
                channelFactory.createChannel(PORTFOLIO_CHANNEL));
        this.deadlineMillis = properties.portfolioDeadline().toMillis();
    }

    /** Portfolio #105의 특정 거래일 EOD 스냅샷 한 페이지를 조회한다. */
    @Override
    public PortfolioSnapshotPage listDailySnapshots(
            LocalDate snapshotDate,
            String pageToken,
            int pageSize) {
        ListDailyPortfolioSnapshotsRequest request = ListDailyPortfolioSnapshotsRequest.newBuilder()
                .setSnapshotDate(snapshotDate.toString())
                .setPage(PageRequest.newBuilder()
                        .setPageSize(pageSize)
                        .setPageToken(pageToken)
                        .build())
                .build();

        try {
            var response = stub.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .listDailyPortfolioSnapshots(request);
            List<PortfolioSnapshotItem> items = response.getSnapshotsList().stream()
                    .map(snapshot -> new PortfolioSnapshotItem(
                            UUID.fromString(snapshot.getUserId()),
                            snapshot.getTotalAsset(),
                            new BigDecimal(snapshot.getCumulativeReturnRate())))
                    .toList();
            return new PortfolioSnapshotPage(items, response.getPage().getNextPageToken());
        } catch (StatusRuntimeException exception) {
            throw new RankingException(
                    RankingErrorCode.PORTFOLIO_SNAPSHOT_SERVICE_UNAVAILABLE,
                    exception);
        } catch (IllegalArgumentException exception) {
            throw new RankingException(RankingErrorCode.INVALID_PORTFOLIO_SNAPSHOT, exception);
        }
    }
}
