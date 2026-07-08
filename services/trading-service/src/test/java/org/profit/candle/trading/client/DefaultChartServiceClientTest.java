package org.profit.candle.trading.client;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.GetPreviousCloseRequest;
import org.profit.candle.proto.stock.v1.GetPreviousCloseResponse;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * DefaultChartServiceClient 단위 테스트. 실제 네트워크 대신 gRPC in-process 채널로
 * 진짜 stub 직렬화/역직렬화 경로와 예외 매핑(StatusRuntimeException → ChartServiceException)을
 * 검증한다. GrpcChannelFactory만 mock으로 대체해 in-process 채널을 주입한다.
 *
 * <p>필요 의존성: {@code testImplementation 'io.grpc:grpc-inprocess:<grpc-bom-version>'}
 * (없으면 io.grpc:grpc-testing 아티팩트에 포함되어 있을 수 있음 — build.gradle에서 확인 필요).</p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultChartServiceClientTest {

    @Mock private GrpcChannelFactory channelFactory;

    private Server server;
    private ManagedChannel channel;
    private DefaultChartServiceClient client;

    private void startServerWithImpl(ChartServiceGrpc.ChartServiceImplBase impl) throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        when(channelFactory.createChannel("chart-service")).thenReturn(channel);
        client = new DefaultChartServiceClient(channelFactory);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Test
    @DisplayName("정상 응답 시 전일 종가를 그대로 반환한다")
    void shouldReturnPreviousCloseOnSuccess() throws IOException {
        startServerWithImpl(new ChartServiceGrpc.ChartServiceImplBase() {
            @Override
            public void getPreviousClose(GetPreviousCloseRequest request,
                                         StreamObserver<GetPreviousCloseResponse> observer) {
                observer.onNext(GetPreviousCloseResponse.newBuilder().setPrevClose(70_000).build());
                observer.onCompleted();
            }
        });

        long price = client.getPreviousClose("005930", LocalDate.of(2026, 7, 6));

        assertThat(price).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("서버가 StatusRuntimeException을 던지면 ChartServiceException으로 감싸 전파한다")
    void shouldWrapStatusRuntimeExceptionAsChartServiceException() throws IOException {
        startServerWithImpl(new ChartServiceGrpc.ChartServiceImplBase() {
            @Override
            public void getPreviousClose(GetPreviousCloseRequest request,
                                         StreamObserver<GetPreviousCloseResponse> observer) {
                observer.onError(Status.UNAVAILABLE.withDescription("chart-service down").asRuntimeException());
            }
        });

        assertThatThrownBy(() -> client.getPreviousClose("005930", LocalDate.of(2026, 7, 6)))
                .isInstanceOf(ChartServiceException.class);
    }

    @Test
    @DisplayName("요청에 symbol과 KST 기준 자정 타임스탬프가 정확히 실려간다")
    void shouldSendSymbolAndKstMidnightTimestampInRequest() throws IOException {
        GetPreviousCloseRequest[] captured = new GetPreviousCloseRequest[1];
        startServerWithImpl(new ChartServiceGrpc.ChartServiceImplBase() {
            @Override
            public void getPreviousClose(GetPreviousCloseRequest request,
                                         StreamObserver<GetPreviousCloseResponse> observer) {
                captured[0] = request;
                observer.onNext(GetPreviousCloseResponse.newBuilder().setPrevClose(1).build());
                observer.onCompleted();
            }
        });

        client.getPreviousClose("005930", LocalDate.of(2026, 7, 6));

        assertThat(captured[0].getCode()).isEqualTo("005930");
        // 2026-07-06 00:00 KST = 2026-07-05 15:00 UTC
        assertThat(captured[0].getDate().getSeconds())
                .isEqualTo(LocalDate.of(2026, 7, 6).atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                        .toInstant().getEpochSecond());
    }
}