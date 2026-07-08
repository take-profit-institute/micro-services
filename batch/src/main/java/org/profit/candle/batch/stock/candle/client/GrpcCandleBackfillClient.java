package org.profit.candle.batch.stock.candle.client;

import com.google.protobuf.Timestamp;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.stock.candle.exception.StockCandleErrorCode;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.profit.candle.proto.stock.v1.BackfillCandlesRequest;
import org.profit.candle.proto.stock.v1.CandleInterval;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.FindExistingCandleCodesRequest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcCandleBackfillClient implements CandleBackfillClient {

    private static final int EXISTING_CODES_QUERY_SIZE = 100;
    private static final Metadata.Key<String> ROLE = Metadata.Key.of(
            "x-role",
            Metadata.ASCII_STRING_MARSHALLER
    );

    private final ChartServiceGrpc.ChartServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcCandleBackfillClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties
    ) {
        this.stub = ChartServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().stockTarget())
        );
        // 백필은 서버가 키움 HTTP를 타 무거우므로 sync와 동일한 긴 deadline을 쓴다.
        this.deadlineMillis = batchProperties.grpc().stockSyncDeadlineMillis();
    }

    @Override
    public int backfillDaily(String code, int count) {
        try {
            Metadata metadata = new Metadata();
            metadata.put(ROLE, "SYSTEM");
            return stub
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .backfillCandles(BackfillCandlesRequest.newBuilder()
                            .setCode(code)
                            .setInterval(CandleInterval.DAY_1)
                            .setCount(count)
                            .build())
                    .getUpserted();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public List<String> findExistingDailyCodes(List<String> codes, Instant openTime) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            for (int from = 0; from < codes.size(); from += EXISTING_CODES_QUERY_SIZE) {
                int to = Math.min(from + EXISTING_CODES_QUERY_SIZE, codes.size());
                result.addAll(findExistingDailyCodesChunk(codes.subList(from, to), openTime));
            }
            return result;
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    private StockCandleException mapException(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        boolean retryable = code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED
                || code == Status.Code.INTERNAL;
        StockCandleErrorCode errorCode = retryable
                ? StockCandleErrorCode.EXTERNAL_CLIENT_RETRYABLE
                : StockCandleErrorCode.EXTERNAL_CLIENT_FAILED;
        return new StockCandleException(errorCode, exception);
    }

    private List<String> findExistingDailyCodesChunk(List<String> codes, Instant openTime) {
        Metadata metadata = new Metadata();
        metadata.put(ROLE, "SYSTEM");
        return stub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .findExistingCandleCodes(FindExistingCandleCodesRequest.newBuilder()
                        .addAllCodes(codes)
                        .setInterval(CandleInterval.DAY_1)
                        .setDate(toTimestamp(openTime))
                        .build())
                .getCodesList();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
