package org.profit.candle.trading.order.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.trading.v1.*;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.entity.ExecutionEntity;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.event.OrderIdempotencyOperations;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.repository.ExecutionRepository;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.order.service.OrderService;
import org.profit.candle.trading.support.idempotency.IdempotencyContext;
import org.profit.candle.trading.support.idempotency.IdempotencyExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OrderGrpcService 단위 테스트. IdempotencyExecutor는 mock으로 대체하되, 넘겨받은
 * command Supplier를 그대로 실행하도록 answer를 걸어 실제 idempotency 알고리즘 없이도
 * orderService 호출 결과가 응답까지 이어지는 흐름을 검증한다 (IdempotencyExecutor 자체의
 * 알고리즘은 IdempotencyExecutorTest에서 별도로 검증됨 — 책임 분리).
 */
@ExtendWith(MockitoExtension.class)
class OrderGrpcServiceTest {

    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private ExecutionRepository executionRepository;
    @Mock private IdempotencyExecutor idempotencyExecutor;
    @Mock private OrderIdempotencyOperations idempotencyOperations;
    @Mock private StreamObserver<PlaceOrderResponse> placeObserver;
    @Mock private StreamObserver<CancelOrderResponse> cancelObserver;
    @Mock private StreamObserver<AmendOrderResponse> amendObserver;
    @Mock private StreamObserver<ListOrdersResponse> listObserver;
    @Mock private StreamObserver<ExpirePendingOrdersResponse> expireObserver;

    private OrderGrpcService grpcService;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        grpcService = new OrderGrpcService(orderService, orderRepository, executionRepository,
                idempotencyExecutor, idempotencyOperations);
        // idempotencyExecutor.execute(...)는 항상 넘겨받은 command를 그대로 실행하도록 위임.
        lenient().when(idempotencyExecutor.execute(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> command = invocation.getArgument(3);
                    return command.get();
                });
    }

    private <T> T withActor(String actorId, String idempotencyKey, Supplier<T> body) {
        Context context = Context.current().withValue(IdempotencyContext.CONTEXT_KEY,
                new IdempotencyContext(actorId, "OrderService/Op", idempotencyKey));
        Context previous = context.attach();
        try {
            return body.get();
        } finally {
            context.detach(previous);
        }
    }

    private void runWithActor(String actorId, Runnable body) {
        withActor(actorId, "idem-key", () -> {
            body.run();
            return null;
        });
    }

    private void runWithActor(String actorId, String idempotencyKey, Runnable body) {
        withActor(actorId, idempotencyKey, () -> {
            body.run();
            return null;
        });
    }

    private OrderEntity pendingLimitOrder() {
        return OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-order-1");
    }

    @Nested
    @DisplayName("listOrders")
    class ListOrders {

        @Test
        void shouldListAllOrdersWhenStatusUnspecified() {
            OrderEntity order = pendingLimitOrder();
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(order));
            when(executionRepository.findByOrderIdIn(List.of(order.getId()))).thenReturn(List.of());
            ListOrdersRequest request = ListOrdersRequest.newBuilder().setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.listOrders(request, listObserver));

            ArgumentCaptor<ListOrdersResponse> captor = ArgumentCaptor.forClass(ListOrdersResponse.class);
            verify(listObserver).onNext(captor.capture());
            assertThat(captor.getValue().getOrdersCount()).isEqualTo(1);
            verify(orderRepository, never()).findByUserIdAndStatusOrderByCreatedAtDesc(any(), any());
        }

        @Test
        void shouldFilterByStatusWhenStatusProvided() {
            when(orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderStatusValue.FILLED))
                    .thenReturn(List.of());
            when(executionRepository.findByOrderIdIn(List.of())).thenReturn(List.of());
            ListOrdersRequest request = ListOrdersRequest.newBuilder()
                    .setUserId(userId.toString())
                    .setStatus(OrderStatus.ORDER_STATUS_FILLED)
                    .build();

            runWithActor(userId.toString(), () -> grpcService.listOrders(request, listObserver));

            verify(orderRepository).findByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderStatusValue.FILLED);
        }

        @Test
        void shouldAttachExecutionOnlyToMatchingOrder() {
            OrderEntity filled = pendingLimitOrder();
            filled.fill();
            ExecutionEntity execution = ExecutionEntity.create(filled.getId(), 70_000L, 10, 105L, 0L, 700_105L,
                    java.time.Instant.now());
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(filled));
            when(executionRepository.findByOrderIdIn(List.of(filled.getId()))).thenReturn(List.of(execution));
            ListOrdersRequest request = ListOrdersRequest.newBuilder().setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.listOrders(request, listObserver));

            ArgumentCaptor<ListOrdersResponse> captor = ArgumentCaptor.forClass(ListOrdersResponse.class);
            verify(listObserver).onNext(captor.capture());
            assertThat(captor.getValue().getOrders(0).getExecutedPrice()).isEqualTo(70_000L);
        }
    }

    @Nested
    @DisplayName("placeOrder")
    class PlaceOrder {

        @Test
        void shouldReturnPlacedOrderOnSuccess() {
            OrderEntity order = pendingLimitOrder();
            when(orderService.placeOrder(eq(userId), any())).thenReturn(order);
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_LIMIT)
                    .setQuantity(10).setPrice(70_000).build();

            runWithActor(userId.toString(), "idem-1", () -> grpcService.placeOrder(request, placeObserver));

            ArgumentCaptor<PlaceOrderResponse> captor = ArgumentCaptor.forClass(PlaceOrderResponse.class);
            verify(placeObserver).onNext(captor.capture());
            verify(placeObserver).onCompleted();
            assertThat(captor.getValue().getOrder().getSymbol()).isEqualTo("005930");
        }

        @Test
        void shouldIncludeExecutionDetailsWhenMarketOrderFillsImmediately() {
            // toProto()의 "execution != null" 분기 — 시장가는 즉시 체결되므로 같은 트랜잭션
            // 안에서 ExecutionEntity가 이미 존재한다. 지금까지는 execution=null 케이스만 탔었음.
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-market");
            order.fill();
            ExecutionEntity execution = ExecutionEntity.create(order.getId(), 70_000L, 10, 105L, 0L,
                    700_105L, java.time.Instant.now());
            when(orderService.placeOrder(eq(userId), any())).thenReturn(order);
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.of(execution));
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_MARKET)
                    .setQuantity(10).build();

            runWithActor(userId.toString(), "idem-market", () -> grpcService.placeOrder(request, placeObserver));

            ArgumentCaptor<PlaceOrderResponse> captor = ArgumentCaptor.forClass(PlaceOrderResponse.class);
            verify(placeObserver).onNext(captor.capture());
            assertThat(captor.getValue().getOrder().getExecutedPrice()).isEqualTo(70_000L);
            assertThat(captor.getValue().getOrder().getNetAmount()).isEqualTo(700_105L);
        }

        @Test
        void shouldThrowInvalidArgumentWhenSideUnspecified() {
            // toSide()가 던지는 StatusRuntimeException은 OrderException이 아니라서
            // catch 블록에 안 걸린다 — observer.onError가 아니라 메서드 자체가 던진다
            // (ReservationGrpcService에서 발견된 것과 동일한 패턴).
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_UNSPECIFIED).setKind(OrderKind.ORDER_KIND_LIMIT)
                    .setQuantity(10).setPrice(70_000).build();

            assertThatThrownBy(() -> runWithActor(userId.toString(), "idem-side", () ->
                    grpcService.placeOrder(request, placeObserver)))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(placeObserver);
        }

        @Test
        void shouldThrowInvalidArgumentWhenKindUnspecified() {
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_UNSPECIFIED)
                    .setQuantity(10).setPrice(70_000).build();

            assertThatThrownBy(() -> runWithActor(userId.toString(), "idem-kind", () ->
                    grpcService.placeOrder(request, placeObserver)))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void shouldThrowInvalidArgumentWhenKindIsAfterHoursClose() {
            // OrderKind에는 AFTER_HOURS_CLOSE도 있지만 즉시 주문(OrderGrpcService)에서는
            // MARKET/LIMIT만 허용된다 — default 분기의 또 다른 진입 경로.
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_AFTER_HOURS_CLOSE)
                    .setQuantity(10).build();

            assertThatThrownBy(() -> runWithActor(userId.toString(), "idem-kind-2", () ->
                    grpcService.placeOrder(request, placeObserver)))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void shouldMapAccountExceptionFromInsufficientBalanceToFailedPrecondition() {
            when(orderService.placeOrder(eq(userId), any()))
                    .thenThrow(new AccountException(AccountErrorCode.INSUFFICIENT_AVAILABLE_BALANCE));
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_LIMIT)
                    .setQuantity(10).setPrice(70_000).build();

            runWithActor(userId.toString(), "idem-2", () -> grpcService.placeOrder(request, placeObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(placeObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }
    }

    /**
     * OrderErrorCode → Status 매핑 switch의 모든 분기를 강제 실행한다.
     * placeOrder를 매개로 쓰지만 실제로는 toGrpcException(OrderErrorCode) 전체를 검증하는 목적이다.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(OrderErrorCode.class)
    @DisplayName("OrderErrorCode 전체 값이 예외 없이 gRPC Status로 매핑된다")
    void shouldMapEveryOrderErrorCodeToSomeGrpcStatus(OrderErrorCode errorCode) {
        when(orderService.placeOrder(eq(userId), any())).thenThrow(new OrderException(errorCode));
        PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                .setUserId(userId.toString()).setSymbol("005930")
                .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_LIMIT)
                .setQuantity(10).setPrice(70_000).build();

        runWithActor(userId.toString(), "idem-x", () -> grpcService.placeOrder(request, placeObserver));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(placeObserver).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
    }

    /** AccountErrorCode 쪽 매핑도(OrderGrpcService에 중복 존재) 마찬가지로 전 분기 실행. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(AccountErrorCode.class)
    @DisplayName("AccountErrorCode 전체 값이 예외 없이 gRPC Status로 매핑된다 (OrderGrpcService 중복 매핑)")
    void shouldMapEveryAccountErrorCodeToSomeGrpcStatus(AccountErrorCode errorCode) {
        when(orderService.placeOrder(eq(userId), any())).thenThrow(new AccountException(errorCode));
        PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                .setUserId(userId.toString()).setSymbol("005930")
                .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_LIMIT)
                .setQuantity(10).setPrice(70_000).build();

        runWithActor(userId.toString(), "idem-y", () -> grpcService.placeOrder(request, placeObserver));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(placeObserver).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        void shouldReturnCancelledOrderAndReleasedAmountOnSuccess() {
            OrderEntity order = pendingLimitOrder();
            order.markCancelled();
            when(orderService.cancelOrder(eq(userId), any())).thenReturn(new CancelResult(order, 700_105L));
            CancelOrderRequest request = CancelOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setOrderId(order.getId().toString()).build();

            runWithActor(userId.toString(), "idem-cancel", () -> grpcService.cancelOrder(request, cancelObserver));

            ArgumentCaptor<CancelOrderResponse> captor = ArgumentCaptor.forClass(CancelOrderResponse.class);
            verify(cancelObserver).onNext(captor.capture());
            assertThat(captor.getValue().getReleasedAmount()).isEqualTo(700_105L);
        }

        @Test
        void shouldMapOrderNotFoundToNotFoundStatus() {
            when(orderService.cancelOrder(eq(userId), any()))
                    .thenThrow(new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
            CancelOrderRequest request = CancelOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setOrderId(UUID.randomUUID().toString()).build();

            runWithActor(userId.toString(), "idem-cancel-2", () -> grpcService.cancelOrder(request, cancelObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(cancelObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("amendOrder")
    class AmendOrder {

        @Test
        void shouldReturnAmendedOrderOnSuccess() {
            OrderEntity amended = OrderEntity.placeWithParent(userId, accountId, "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 5, 80_000L, 400_060L,
                    "idem-amend", UUID.randomUUID());
            when(orderService.amendOrder(eq(userId), any(), any())).thenReturn(amended);
            AmendOrderRequest request = AmendOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setOrderId(UUID.randomUUID().toString())
                    .setQuantity(5).setPrice(80_000).build();

            runWithActor(userId.toString(), "idem-amend", () -> grpcService.amendOrder(request, amendObserver));

            ArgumentCaptor<AmendOrderResponse> captor = ArgumentCaptor.forClass(AmendOrderResponse.class);
            verify(amendObserver).onNext(captor.capture());
            assertThat(captor.getValue().getOrder().getQuantity()).isEqualTo(5);
        }

        @Test
        void shouldMapMarketOrderCannotBeCancelledToFailedPrecondition() {
            when(orderService.amendOrder(eq(userId), any(), any()))
                    .thenThrow(new OrderException(OrderErrorCode.MARKET_ORDER_CANNOT_BE_CANCELLED));
            AmendOrderRequest request = AmendOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setOrderId(UUID.randomUUID().toString())
                    .setQuantity(5).setPrice(80_000).build();

            runWithActor(userId.toString(), "idem-amend-2", () -> grpcService.amendOrder(request, amendObserver));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(amendObserver).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }
    }

    @Nested
    @DisplayName("expirePendingOrders")
    class ExpirePendingOrders {

        @Test
        void shouldReturnCancelledCountWithoutActorOrIdempotencyCheck() {
            when(orderService.expirePendingOrders()).thenReturn(3);
            ExpirePendingOrdersRequest request = ExpirePendingOrdersRequest.newBuilder().build();

            // 배치 전용 RPC — Context에 actor를 attach하지 않고 호출해도 성공해야 한다.
            grpcService.expirePendingOrders(request, expireObserver);

            ArgumentCaptor<ExpirePendingOrdersResponse> captor =
                    ArgumentCaptor.forClass(ExpirePendingOrdersResponse.class);
            verify(expireObserver).onNext(captor.capture());
            assertThat(captor.getValue().getCancelledCount()).isEqualTo(3);
            verify(expireObserver).onCompleted();
        }
    }

    @Nested
    @DisplayName("requireActor 공통 검증")
    class RequireActor {

        @Test
        void shouldThrowUnauthenticatedWhenNoContextAttached() {
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(userId.toString()).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_MARKET)
                    .setQuantity(10).build();

            // requireActor()는 try-catch 밖(placeOrder 진입 직후)에서 던져지므로
            // observer.onError가 아니라 메서드 호출 자체가 StatusRuntimeException을 던진다.
            assertThatThrownBy(() -> grpcService.placeOrder(request, placeObserver))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);
        }

        @Test
        void shouldThrowPermissionDeniedWhenRequestUserIdMismatchesActor() {
            String otherUserId = UUID.randomUUID().toString();
            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setUserId(otherUserId).setSymbol("005930")
                    .setSide(OrderSide.ORDER_SIDE_BUY).setKind(OrderKind.ORDER_KIND_MARKET)
                    .setQuantity(10).build();

            assertThatThrownBy(() -> withActor(userId.toString(), "idem", () -> {
                grpcService.placeOrder(request, placeObserver);
                return null;
            }))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED);
        }
    }
}