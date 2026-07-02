package org.profit.candle.trading.client;

import com.google.protobuf.Timestamp;
import io.grpc.Deadline;
import io.grpc.StatusRuntimeException;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.GetPreviousCloseRequest;
import org.profit.candle.proto.stock.v1.GetPreviousCloseResponse;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * {@link ChartServiceClient} gRPC 구현체.
 *
 * <p>spring-grpc 1.1.0의 {@link GrpcChannelFactory}를 통해 application.yml의
 * {@code spring.grpc.client.channels.chart-service.address}로 채널을 생성한다.
 * EKS 배포 시 환경변수 {@code CHART_SERVICE_GRPC_ADDRESS}만 교체하면 된다.</p>
 *
 * <p>배치 체결 흐름에서 호출되므로 per-call deadline을 설정한다 — ChartService가
 * 느리거나 응답하지 않을 때 배치가 무한 대기하는 것을 방지한다 (Qodo #3).</p>
 */
@Component
public class DefaultChartServiceClient implements ChartServiceClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEADLINE_SECONDS = 5;

    private final ChartServiceGrpc.ChartServiceBlockingStub stub;

    public DefaultChartServiceClient(GrpcChannelFactory channelFactory) {
        this.stub = ChartServiceGrpc.newBlockingStub(
                channelFactory.createChannel("chart-service"));
    }

    @Override
    public long getPreviousClose(String symbol, LocalDate baseDate) {
        var baseDateInstant = baseDate.atStartOfDay(KST).toInstant();
        var timestamp = Timestamp.newBuilder()
                .setSeconds(baseDateInstant.getEpochSecond())
                .setNanos(baseDateInstant.getNano())
                .build();

        GetPreviousCloseRequest request = GetPreviousCloseRequest.newBuilder()
                .setCode(symbol)
                .setDate(timestamp)
                .build();

        try {
            // per-call deadline: 5초 초과 시 DEADLINE_EXCEEDED로 실패 처리
            GetPreviousCloseResponse response = stub
                    .withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                    .getPreviousClose(request);
            return response.getPrevClose();
        } catch (StatusRuntimeException e) {
            throw new ChartServiceException(symbol, e);
        }
    }
}