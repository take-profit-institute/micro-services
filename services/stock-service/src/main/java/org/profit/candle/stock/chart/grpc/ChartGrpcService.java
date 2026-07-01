package org.profit.candle.stock.chart.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.proto.stock.v1.BackfillCandlesRequest;
import org.profit.candle.proto.stock.v1.BackfillCandlesResponse;
import org.profit.candle.proto.stock.v1.Candle;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.GetCandlesRequest;
import org.profit.candle.proto.stock.v1.GetCandlesResponse;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.service.CandleBackfillService;
import org.profit.candle.stock.chart.service.ChartService;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChartGrpcService extends ChartServiceGrpc.ChartServiceImplBase {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final ChartService chartService;
    private final CandleBackfillService backfillService;

    @Override
    public void getCandles(GetCandlesRequest request, StreamObserver<GetCandlesResponse> observer) {
        try {
            org.profit.candle.stock.chart.dto.CandleInterval interval = intervalOf(request.getInterval());
            int limit = normalizeLimit(request.getLimit());
            Instant to = request.hasTo() ? toInstant(request.getTo()) : null;
            GetCandlesResponse.Builder builder = GetCandlesResponse.newBuilder();
            chartService.getCandles(request.getCode(), interval, limit, to)
                    .forEach(candle -> builder.addCandles(toProto(candle)));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(ChartErrorCode.INVALID_CANDLE_REQUEST.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void backfillCandles(BackfillCandlesRequest request, StreamObserver<BackfillCandlesResponse> observer) {
        try {
            org.profit.candle.stock.chart.dto.CandleInterval interval = intervalOf(request.getInterval());
            int count = normalizeLimit(request.getCount());
            int upserted = backfillService.backfill(request.getCode(), interval, count, null);
            observer.onNext(BackfillCandlesResponse.newBuilder().setUpserted(upserted).build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(ChartErrorCode.INVALID_CANDLE_REQUEST.code())
                    .asRuntimeException());
        }
    }

    private static Candle toProto(CandleResult result) {
        return Candle.newBuilder()
                .setCode(result.code())
                .setInterval(toProtoInterval(result.interval()))
                .setOpenTime(toTimestamp(result.openTime()))
                .setOpen(result.open())
                .setHigh(result.high())
                .setLow(result.low())
                .setClose(result.close())
                .setVolume(result.volume())
                .setClosed(result.closed())
                .build();
    }

    private static org.profit.candle.stock.chart.dto.CandleInterval intervalOf(
            org.profit.candle.proto.stock.v1.CandleInterval interval) {
        return switch (interval) {
            case DAY_1 -> org.profit.candle.stock.chart.dto.CandleInterval.DAY_1;
            case WEEK_1 -> org.profit.candle.stock.chart.dto.CandleInterval.WEEK_1;
            case MONTH_1 -> org.profit.candle.stock.chart.dto.CandleInterval.MONTH_1;
            default -> throw new IllegalArgumentException("unsupported candle interval");
        };
    }

    private static org.profit.candle.proto.stock.v1.CandleInterval toProtoInterval(
            org.profit.candle.stock.chart.dto.CandleInterval interval) {
        return switch (interval) {
            case DAY_1 -> org.profit.candle.proto.stock.v1.CandleInterval.DAY_1;
            case WEEK_1 -> org.profit.candle.proto.stock.v1.CandleInterval.WEEK_1;
            case MONTH_1 -> org.profit.candle.proto.stock.v1.CandleInterval.MONTH_1;
        };
    }

    private static int normalizeLimit(int requested) {
        if (requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Status toGrpcStatus(CandleException e) {
        if (e.errorCode() instanceof ChartErrorCode code) {
            return switch (code) {
                case INVALID_CANDLE_REQUEST -> Status.INVALID_ARGUMENT.withDescription(code.code());
                case CHART_DATA_UNAVAILABLE -> Status.UNAVAILABLE.withDescription(code.code());
            };
        }
        return Status.INTERNAL.withDescription(e.errorCode().code());
    }
}
