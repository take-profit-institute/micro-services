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
import org.profit.candle.proto.stock.v1.CloseDailyCandlesRequest;
import org.profit.candle.proto.stock.v1.CloseDailyCandlesResponse;
import org.profit.candle.proto.stock.v1.GetCandlesRequest;
import org.profit.candle.proto.stock.v1.GetCandlesResponse;
import org.profit.candle.proto.stock.v1.GetPreviousCloseRequest;
import org.profit.candle.proto.stock.v1.GetPreviousCloseResponse;
import org.profit.candle.proto.stock.v1.GetPriceStatsRequest;
import org.profit.candle.proto.stock.v1.GetPriceStatsResponse;
import org.profit.candle.proto.stock.v1.GetSparklinesRequest;
import org.profit.candle.proto.stock.v1.GetSparklinesResponse;
import org.profit.candle.proto.stock.v1.Sparkline;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.dto.PriceStatsResult;
import org.profit.candle.stock.chart.dto.SparklineResult;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.service.CandleBackfillService;
import org.profit.candle.stock.chart.service.ChartService;
import org.profit.candle.stock.chart.service.DailyCloseService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class ChartGrpcService extends ChartServiceGrpc.ChartServiceImplBase {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final ChartService chartService;
    private final CandleBackfillService backfillService;
    private final DailyCloseService dailyCloseService;

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
    public void getSparklines(GetSparklinesRequest request, StreamObserver<GetSparklinesResponse> observer) {
        try {
            org.profit.candle.stock.chart.dto.CandleInterval interval = intervalOrDefault(request.getInterval());
            GetSparklinesResponse.Builder builder = GetSparklinesResponse.newBuilder();
            chartService.getSparklines(request.getCodesList(), interval, request.getPoints())
                    .forEach(sparkline -> builder.addSparklines(toProto(sparkline)));
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
    public void getPreviousClose(GetPreviousCloseRequest request, StreamObserver<GetPreviousCloseResponse> observer) {
        try {
            if (!request.hasDate()) {
                throw new IllegalArgumentException("date is required");
            }
            CandleResult prev = chartService.getPreviousClose(request.getCode(), toInstant(request.getDate()));
            observer.onNext(GetPreviousCloseResponse.newBuilder()
                    .setCode(prev.code())
                    .setPrevClose(prev.close())
                    .setPrevOpenTime(toTimestamp(prev.openTime()))
                    .build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(ChartErrorCode.INVALID_CANDLE_REQUEST.code())
                    .asRuntimeException());
        }
    }

    @Override
    public void getPriceStats(GetPriceStatsRequest request, StreamObserver<GetPriceStatsResponse> observer) {
        try {
            PriceStatsResult stats = chartService.getPriceStats(request.getCode(), request.getWindowDays());
            GetPriceStatsResponse.Builder builder = GetPriceStatsResponse.newBuilder()
                    .setCode(stats.code())
                    .setHigh(stats.high())
                    .setLow(stats.low())
                    .setLatestClose(stats.latestClose())
                    .setLatestVolume(stats.latestVolume());
            if (stats.asOf() != null) {
                builder.setAsOf(toTimestamp(stats.asOf()));
            }
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

    @Override
    public void closeDailyCandles(CloseDailyCandlesRequest request, StreamObserver<CloseDailyCandlesResponse> observer) {
        try {
            LocalDate tradeDate = LocalDate.parse(request.getTradeDate());
            int closed = dailyCloseService.closeDaily(tradeDate);
            observer.onNext(CloseDailyCandlesResponse.newBuilder().setClosedCount(closed).build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        } catch (DateTimeParseException | IllegalArgumentException e) {
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

    private static Sparkline toProto(SparklineResult result) {
        return Sparkline.newBuilder()
                .setCode(result.code())
                .addAllCloses(result.closes())
                .setLastOpenTime(toTimestamp(result.lastOpenTime()))
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

    /** sparkline 은 주기 미지정 시 DAY_1 로 본다. */
    private static org.profit.candle.stock.chart.dto.CandleInterval intervalOrDefault(
            org.profit.candle.proto.stock.v1.CandleInterval interval) {
        if (interval == org.profit.candle.proto.stock.v1.CandleInterval.CANDLE_INTERVAL_UNSPECIFIED) {
            return org.profit.candle.stock.chart.dto.CandleInterval.DAY_1;
        }
        return intervalOf(interval);
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
