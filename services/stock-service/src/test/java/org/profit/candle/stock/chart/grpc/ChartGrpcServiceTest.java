package org.profit.candle.stock.chart.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.proto.stock.v1.BackfillCandlesRequest;
import org.profit.candle.proto.stock.v1.BackfillCandlesResponse;
import org.profit.candle.proto.stock.v1.GetCandlesRequest;
import org.profit.candle.proto.stock.v1.GetCandlesResponse;
import org.profit.candle.proto.stock.v1.GetPriceStatsRequest;
import org.profit.candle.proto.stock.v1.GetPriceStatsResponse;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.dto.PriceStatsResult;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.service.CandleBackfillService;
import org.profit.candle.stock.chart.service.ChartService;
import org.profit.candle.stock.chart.service.DailyCloseService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChartGrpcServiceTest {

    @Mock ChartService chartService;
    @Mock CandleBackfillService backfillService;
    @Mock DailyCloseService dailyCloseService;

    @Test
    void getCandles_returnsProtoCandles() {
        when(chartService.getCandles("005930", CandleInterval.DAY_1, 60, null))
                .thenReturn(List.of(new CandleResult("005930", CandleInterval.DAY_1,
                        Instant.parse("2026-06-30T00:00:00Z"), 70000, 71000, 69000, 70500, 1000, true)));
        ChartGrpcService service = new ChartGrpcService(chartService, backfillService, dailyCloseService);
        CapturingObserver<GetCandlesResponse> observer = new CapturingObserver<>();

        service.getCandles(GetCandlesRequest.newBuilder()
                .setCode("005930")
                .setInterval(org.profit.candle.proto.stock.v1.CandleInterval.DAY_1)
                .setLimit(60)
                .build(), observer);

        assertThat(observer.value.getCandlesCount()).isEqualTo(1);
        assertThat(observer.value.getCandles(0).getClose()).isEqualTo(70500);
        assertThat(observer.completed).isTrue();
    }

    @Test
    void getCandles_mapsUnavailableError() {
        when(chartService.getCandles("005930", CandleInterval.DAY_1, 1, null))
                .thenThrow(new CandleException(ChartErrorCode.CHART_DATA_UNAVAILABLE));
        ChartGrpcService service = new ChartGrpcService(chartService, backfillService, dailyCloseService);
        CapturingObserver<GetCandlesResponse> observer = new CapturingObserver<>();

        service.getCandles(GetCandlesRequest.newBuilder()
                .setCode("005930")
                .setInterval(org.profit.candle.proto.stock.v1.CandleInterval.DAY_1)
                .setLimit(1)
                .build(), observer);

        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(error.getStatus().getDescription()).isEqualTo("CHART_DATA_UNAVAILABLE");
    }

    @Test
    void backfillCandles_returnsUpsertedCount() {
        when(backfillService.backfill("005930", CandleInterval.WEEK_1, 30, null)).thenReturn(7);
        ChartGrpcService service = new ChartGrpcService(chartService, backfillService, dailyCloseService);
        CapturingObserver<BackfillCandlesResponse> observer = new CapturingObserver<>();

        service.backfillCandles(BackfillCandlesRequest.newBuilder()
                .setCode("005930")
                .setInterval(org.profit.candle.proto.stock.v1.CandleInterval.WEEK_1)
                .setCount(30)
                .build(), observer);

        assertThat(observer.value.getUpserted()).isEqualTo(7);
        assertThat(observer.completed).isTrue();
    }

    @Test
    void getPriceStats_returnsProtoStats() {
        Instant asOf = Instant.parse("2026-06-30T00:00:00Z");
        when(chartService.getPriceStats("005930", 0))
                .thenReturn(new PriceStatsResult("005930", 88000, 55000, 72000, 1500, asOf));
        ChartGrpcService service = new ChartGrpcService(chartService, backfillService, dailyCloseService);
        CapturingObserver<GetPriceStatsResponse> observer = new CapturingObserver<>();

        service.getPriceStats(GetPriceStatsRequest.newBuilder().setCode("005930").build(), observer);

        assertThat(observer.value.getHigh()).isEqualTo(88000);
        assertThat(observer.value.getLow()).isEqualTo(55000);
        assertThat(observer.value.getLatestClose()).isEqualTo(72000);
        assertThat(observer.value.getLatestVolume()).isEqualTo(1500);
        assertThat(observer.value.getAsOf().getSeconds()).isEqualTo(asOf.getEpochSecond());
        assertThat(observer.completed).isTrue();
    }

    @Test
    void getPriceStats_omitsAsOfWhenEmpty() {
        when(chartService.getPriceStats("005930", 0)).thenReturn(PriceStatsResult.empty("005930"));
        ChartGrpcService service = new ChartGrpcService(chartService, backfillService, dailyCloseService);
        CapturingObserver<GetPriceStatsResponse> observer = new CapturingObserver<>();

        service.getPriceStats(GetPriceStatsRequest.newBuilder().setCode("005930").build(), observer);

        assertThat(observer.value.hasAsOf()).isFalse();
        assertThat(observer.value.getHigh()).isZero();
        assertThat(observer.completed).isTrue();
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
