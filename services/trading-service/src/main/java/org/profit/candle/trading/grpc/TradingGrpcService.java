package org.profit.candle.trading.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.trading.v1.AccountBalance;
import org.profit.candle.proto.trading.v1.CancelOrderRequest;
import org.profit.candle.proto.trading.v1.CancelOrderResponse;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.profit.candle.proto.trading.v1.GetBalanceResponse;
import org.profit.candle.proto.trading.v1.ListOrdersRequest;
import org.profit.candle.proto.trading.v1.ListOrdersResponse;
import org.profit.candle.proto.trading.v1.Order;
import org.profit.candle.proto.trading.v1.OrderKind;
import org.profit.candle.proto.trading.v1.OrderSide;
import org.profit.candle.proto.trading.v1.OrderStatus;
import org.profit.candle.proto.trading.v1.PlaceOrderRequest;
import org.profit.candle.proto.trading.v1.PlaceOrderResponse;
import org.profit.candle.proto.trading.v1.TradingServiceGrpc;
import org.profit.candle.trading.domain.OrderKindValue;
import org.profit.candle.trading.domain.OrderSideValue;
import org.profit.candle.trading.domain.OrderStatusValue;
import org.profit.candle.trading.domain.TradingDomainService;
import org.profit.candle.trading.domain.entity.AccountBalanceEntity;
import org.profit.candle.trading.domain.entity.OrderEntity;
import org.profit.candle.trading.domain.repository.OrderRepository;
import org.profit.candle.trading.idempotency.IdempotencyContext;
import org.profit.candle.trading.idempotency.IdempotencyExecutor;
import org.springframework.stereotype.Component;

/**
 * TradingService gRPC 엔드포인트.
 *
 * 쓰기 RPC(PlaceOrder/CancelOrder)는 {@link IdempotencyExecutor}를 명시적으로 호출해
 * 멱등성 처리(스펙 §5)를 보이게 한다. 읽기는 바로 조회한다.
 */
@Component
@RequiredArgsConstructor
public class TradingGrpcService extends TradingServiceGrpc.TradingServiceImplBase {

    private final TradingDomainService domain;
    private final OrderRepository orderRepository;
    private final IdempotencyExecutor idempotencyExecutor;

    // ── 읽기 ──────────────────────────────────────────────────────────
    @Override
    public void getBalance(GetBalanceRequest request, StreamObserver<GetBalanceResponse> observer) {
        String actor = requireActor(request.getUserId());
        AccountBalanceEntity balance = domain.getBalance(actor);
        observer.onNext(GetBalanceResponse.newBuilder().setBalance(toProto(balance)).build());
        observer.onCompleted();
    }

    @Override
    public void listOrders(ListOrdersRequest request, StreamObserver<ListOrdersResponse> observer) {
        String actor = requireActor(request.getUserId());
        ListOrdersResponse.Builder response = ListOrdersResponse.newBuilder();
        var orders = request.getStatus() == OrderStatus.ORDER_STATUS_UNSPECIFIED
                ? orderRepository.findByUserIdOrderByCreatedAtDesc(actor)
                : orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(actor, toStatus(request.getStatus()));
        orders.forEach(order -> response.addOrders(toProto(order)));
        observer.onNext(response.build());
        observer.onCompleted();
    }

    // ── 쓰기 (멱등) ───────────────────────────────────────────────────
    @Override
    public void placeOrder(PlaceOrderRequest request, StreamObserver<PlaceOrderResponse> observer) {
        String actor = requireActor(request.getUserId());
        var command = new TradingDomainService.PlaceOrderCommand(
                request.getSymbol(), toSide(request.getSide()), toKind(request.getKind()),
                request.getQuantity(), request.getPrice());

        PlaceOrderResponse response = idempotencyExecutor.execute(
                request,
                PlaceOrderResponse.parser(),
                () -> PlaceOrderResponse.newBuilder()
                        .setOrder(toProto(domain.placeOrder(actor, command)))
                        .build());

        observer.onNext(response);
        observer.onCompleted();
    }

    @Override
    public void cancelOrder(CancelOrderRequest request, StreamObserver<CancelOrderResponse> observer) {
        String actor = requireActor(request.getUserId());

        CancelOrderResponse response = idempotencyExecutor.execute(
                request,
                CancelOrderResponse.parser(),
                () -> {
                    TradingDomainService.CancelResult result = domain.cancelOrder(actor, request.getOrderId());
                    return CancelOrderResponse.newBuilder()
                            .setOrder(toProto(result.order()))
                            .setReleasedAmount(result.releasedAmount())
                            .build();
                });

        observer.onNext(response);
        observer.onCompleted();
    }

    // ── actor 검증 (인증값은 metadata만 신뢰, request.user_id는 일치만 검사) ──
    private String requireActor(String requestUserId) {
        IdempotencyContext context = IdempotencyContext.current();
        String actor = context == null ? null : context.actorId();
        if (actor == null || actor.isBlank()) {
            throw Status.UNAUTHENTICATED.withDescription("MISSING_ACTOR").asRuntimeException();
        }
        if (requestUserId != null && !requestUserId.isBlank() && !requestUserId.equals(actor)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("user_id does not match authenticated actor")
                    .asRuntimeException();
        }
        return actor;
    }

    // ── 매핑 ──────────────────────────────────────────────────────────
    private Order toProto(OrderEntity order) {
        Order.Builder builder = Order.newBuilder()
                .setId(order.id())
                .setUserId(order.userId())
                .setSymbol(order.symbol())
                .setSide(toProtoSide(order.side()))
                .setKind(toProtoKind(order.kind()))
                .setQuantity(order.quantity())
                .setPrice(order.price())
                .setStatus(toProtoStatus(order.status()))
                .setReservedAmount(order.reservedAmount());
        if (order.parentOrderId() != null) {
            builder.setParentOrderId(order.parentOrderId());
        }
        Instant createdAt = order.createdAt() != null ? order.createdAt() : Instant.now();
        builder.setCreatedAt(toTimestamp(createdAt));
        return builder.build();
    }

    private AccountBalance toProto(AccountBalanceEntity balance) {
        return AccountBalance.newBuilder()
                .setUserId(balance.userId())
                .setCash(balance.cash())
                .setReservedBalance(balance.reservedBalance())
                .setAvailableCash(balance.availableCash())
                .setTotalAsset(balance.cash())
                .build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private OrderSideValue toSide(OrderSide side) {
        return switch (side) {
            case ORDER_SIDE_BUY -> OrderSideValue.BUY;
            case ORDER_SIDE_SELL -> OrderSideValue.SELL;
            default -> throw Status.INVALID_ARGUMENT.withDescription("side가 필요합니다").asRuntimeException();
        };
    }

    private OrderKindValue toKind(OrderKind kind) {
        return switch (kind) {
            case ORDER_KIND_MARKET -> OrderKindValue.MARKET;
            case ORDER_KIND_LIMIT -> OrderKindValue.LIMIT;
            case ORDER_KIND_AFTER_HOURS_CLOSE -> OrderKindValue.AFTER_HOURS_CLOSE;
            default -> throw Status.INVALID_ARGUMENT.withDescription("kind가 필요합니다").asRuntimeException();
        };
    }

    private OrderStatusValue toStatus(OrderStatus status) {
        return switch (status) {
            case ORDER_STATUS_PENDING -> OrderStatusValue.PENDING;
            case ORDER_STATUS_FILLED -> OrderStatusValue.FILLED;
            case ORDER_STATUS_CANCELLED -> OrderStatusValue.CANCELLED;
            case ORDER_STATUS_REJECTED -> OrderStatusValue.REJECTED;
            default -> throw Status.INVALID_ARGUMENT.withDescription("알 수 없는 status").asRuntimeException();
        };
    }

    private OrderSide toProtoSide(OrderSideValue side) {
        return side == OrderSideValue.BUY ? OrderSide.ORDER_SIDE_BUY : OrderSide.ORDER_SIDE_SELL;
    }

    private OrderKind toProtoKind(OrderKindValue kind) {
        return switch (kind) {
            case MARKET -> OrderKind.ORDER_KIND_MARKET;
            case LIMIT -> OrderKind.ORDER_KIND_LIMIT;
            case AFTER_HOURS_CLOSE -> OrderKind.ORDER_KIND_AFTER_HOURS_CLOSE;
        };
    }

    private OrderStatus toProtoStatus(OrderStatusValue status) {
        return switch (status) {
            case PENDING -> OrderStatus.ORDER_STATUS_PENDING;
            case FILLED -> OrderStatus.ORDER_STATUS_FILLED;
            case CANCELLED -> OrderStatus.ORDER_STATUS_CANCELLED;
            case REJECTED -> OrderStatus.ORDER_STATUS_REJECTED;
        };
    }
}
