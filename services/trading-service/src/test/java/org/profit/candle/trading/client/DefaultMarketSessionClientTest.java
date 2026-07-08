package org.profit.candle.trading.client;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.market.v1.GetMarketStatusRequest;
import org.profit.candle.proto.market.v1.GetMarketStatusResponse;
import org.profit.candle.proto.market.v1.IsTradingDayRequest;
import org.profit.candle.proto.market.v1.IsTradingDayResponse;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * DefaultMarketSessionClient 단위 테스트 (in-process gRPC). 이 클래스는 예외를 감싸지 않고
 * 그대로 전파하는 fail-safe 설계라 분기 자체는 거의 없지만, 두 RPC(getMarketStatus/
 * isTradingDay) 요청 매핑과 응답 매핑이 정확한지, 그리고 실패 시 StatusRuntimeException이
 * 감싸지지 않고 그대로 전파되는지(의도된 동작)를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultMarketSessionClientTest {

    @Mock private GrpcChannelFactory channelFactory;

    private Server server;
    private ManagedChannel channel;
    private DefaultMarketSessionClient client;

    private void startServerWithImpl(MarketServiceGrpc.MarketServiceImplBase impl) throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        when(channelFactory.createChannel("market-service")).thenReturn(channel);
        client = new DefaultMarketSessionClient(channelFactory);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Nested
    @DisplayName("isMarketOpen")
    class IsMarketOpen {

        @Test
        @DisplayName("응답의 open 필드를 그대로 반환한다")
        void shouldReturnOpenFlagFromResponse() throws IOException {
            startServerWithImpl(new MarketServiceGrpc.MarketServiceImplBase() {
                @Override
                public void getMarketStatus(GetMarketStatusRequest request,
                                            StreamObserver<GetMarketStatusResponse> observer) {
                    observer.onNext(GetMarketStatusResponse.newBuilder().setOpen(true).build());
                    observer.onCompleted();
                }
            });

            assertThat(client.isMarketOpen()).isTrue();
        }

        @Test
        @DisplayName("서버 오류 시 StatusRuntimeException을 감싸지 않고 그대로 전파한다 (fail-safe)")
        void shouldPropagateStatusRuntimeExceptionUnwrapped() throws IOException {
            startServerWithImpl(new MarketServiceGrpc.MarketServiceImplBase() {
                @Override
                public void getMarketStatus(GetMarketStatusRequest request,
                                            StreamObserver<GetMarketStatusResponse> observer) {
                    observer.onError(Status.UNAVAILABLE.asRuntimeException());
                }
            });

            assertThatThrownBy(() -> client.isMarketOpen())
                    .isInstanceOf(io.grpc.StatusRuntimeException.class);
        }
    }

    @Nested
    @DisplayName("isTradingDay")
    class IsTradingDay {

        @Test
        @DisplayName("날짜를 ISO-8601 문자열로 전송하고 응답의 tradingDay 필드를 반환한다")
        void shouldSendIsoDateAndReturnTradingDayFlag() throws IOException {
            IsTradingDayRequest[] captured = new IsTradingDayRequest[1];
            startServerWithImpl(new MarketServiceGrpc.MarketServiceImplBase() {
                @Override
                public void isTradingDay(IsTradingDayRequest request,
                                         StreamObserver<IsTradingDayResponse> observer) {
                    captured[0] = request;
                    observer.onNext(IsTradingDayResponse.newBuilder().setTradingDay(true).build());
                    observer.onCompleted();
                }
            });

            boolean result = client.isTradingDay(LocalDate.of(2026, 7, 7));

            assertThat(result).isTrue();
            assertThat(captured[0].getDate()).isEqualTo("2026-07-07");
        }

        @Test
        @DisplayName("주말/휴장일이면 false를 반환한다")
        void shouldReturnFalseForNonTradingDay() throws IOException {
            startServerWithImpl(new MarketServiceGrpc.MarketServiceImplBase() {
                @Override
                public void isTradingDay(IsTradingDayRequest request,
                                         StreamObserver<IsTradingDayResponse> observer) {
                    observer.onNext(IsTradingDayResponse.newBuilder().setTradingDay(false).build());
                    observer.onCompleted();
                }
            });

            assertThat(client.isTradingDay(LocalDate.of(2026, 7, 11))).isFalse(); // 토요일 가정
        }
    }
}