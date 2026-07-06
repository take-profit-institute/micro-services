package org.profit.candle.batch.ranking.client;

import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.ranking.config.RankingBatchProperties;
import org.profit.candle.batch.ranking.exception.RankingBatchErrorCode;
import org.profit.candle.batch.ranking.exception.RankingBatchException;
import org.profit.candle.proto.common.v1.CommandMetadata;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingRequest;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingResponse;
import org.profit.candle.proto.ranking.v1.RankingServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

/** Ranking Service의 FinalizeDailyRanking RPC를 호출한다. */
@Component
public class GrpcRankingBatchClient implements RankingBatchClient {

    private static final String ACTOR_ID = "batch-service";
    private static final Metadata.Key<String> USER_ID = key("x-user-id");
    private static final Metadata.Key<String> ROLE = key("x-role");
    private static final Metadata.Key<String> IDEMPOTENCY_KEY = key("x-idempotency-key");

    private final RankingServiceGrpc.RankingServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcRankingBatchClient(
            GrpcChannelFactory channelFactory,
            RankingBatchProperties properties
    ) {
        Channel channel = channelFactory.createChannel(properties.grpcTarget());
        this.stub = RankingServiceGrpc.newBlockingStub(channel);
        this.deadlineMillis = properties.deadlineMillis();
    }

    /** metadata와 request에 같은 멱등성 키를 담아 일별 랭킹을 확정한다. */
    @Override
    public Result finalizeDailyRanking(LocalDate rankingDate, String idempotencyKey) {
        Metadata metadata = new Metadata();
        metadata.put(USER_ID, ACTOR_ID);
        metadata.put(ROLE, "SYSTEM");
        metadata.put(IDEMPOTENCY_KEY, idempotencyKey);

        try {
            FinalizeDailyRankingResponse response = stub
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .finalizeDailyRanking(FinalizeDailyRankingRequest.newBuilder()
                            .setRankingDate(rankingDate.toString())
                            .setCommandMetadata(CommandMetadata.newBuilder()
                                    .setIdempotencyKey(idempotencyKey)
                                    .build())
                            .build());
            LocalDate responseDate = parseDate(response.getRankingDate());
            if (!responseDate.equals(rankingDate) || response.getRankedUserCount() < 0) {
                throw new RankingBatchException(RankingBatchErrorCode.RESPONSE_INVALID);
            }
            return new Result(responseDate, response.getRankedUserCount());
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    /** Ranking 응답 날짜를 검증 가능한 LocalDate로 변환한다. */
    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new RankingBatchException(RankingBatchErrorCode.RESPONSE_INVALID, exception);
        }
    }

    /** gRPC 상태에 따라 재시도 가능 여부를 분류한다. */
    private RankingBatchException mapException(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        boolean retryable = code == Status.Code.INTERNAL
                || code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED;
        RankingBatchErrorCode errorCode = retryable
                ? RankingBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE
                : RankingBatchErrorCode.EXTERNAL_CLIENT_FAILED;
        return new RankingBatchException(errorCode, exception);
    }

    private static Metadata.Key<String> key(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }
}
