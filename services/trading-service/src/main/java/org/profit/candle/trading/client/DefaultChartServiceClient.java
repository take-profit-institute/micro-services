package org.profit.candle.trading.client;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * {@link ChartServiceClient} gRPC 구현체.
 *
 * <p>spring-grpc 1.1.0의 {@link GrpcChannelFactory}를 통해 application.yml의
 * {@code spring.grpc.client.channels.chart-service.address}로 채널을 생성한다.
 * EKS 배포 시 환경변수 {@code CHART_SERVICE_GRPC_ADDRESS}만 교체하면 된다.</p>
 *
 * <p>{@code baseDate}는 KST 기준 해당 일자 00:00을 UTC Timestamp로 변환해 넘긴다 —
 * ChartService proto 명세: "이 시각보다 앞선 가장 최근 일봉을 찾는다"</p>
 */

@Component
public class DefaultChartServiceClient implements ChartServiceClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChartServiceGrpc.ChartServiceBlockingStub stub;

    public DefaultChartServiceClient(GrpcChannelFactory channelFactory) {
        this.stub = ChartServiceGrpc.newBlockingStub(
                channelFactory.createChannel("chart-service"));
    }

    @Override
    public long getPreviousClose(String symbol, LocalDate baseDate) {
        // baseDate 00:00 KST → UTC Timestamp
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
            GetPreviousCloseResponse response = stub.getPreviousClose(request);
            return response.getPrevClose();
        } catch (StatusRuntimeException e) {
            throw new ChartServiceException(symbol, e);
        }
    }
}
