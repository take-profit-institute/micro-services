package org.profit.candle.trading.order.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.trading.v1.*;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.event.OrderIdempotencyOperations;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.order.service.OrderService;
import org.profit.candle.trading.support.idempotency.IdempotencyContext;
import org.profit.candle.trading.support.idempotency.IdempotencyExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * OrderService gRPC 엔드포인트.
 *
 * 쓰기 RPC(PlaceOrder/CancelOrder)는 {@link IdempotencyExecutor}를 명시적으로 호출해
 * 멱등성 처리(스펙 §5)를 보이게 한다. 읽기는 바로 조회한다.
 *
 * AmendOrder는 도메인 후속 작업으로 남긴다 (proto 계약은 이미 존재).
 *
 * <p>OrderException/AccountException → gRPC Status 매핑을 이 클래스가 직접 한다.
 * AccountErrorCode 매핑은 AccountGrpcService에도 동일하게 존재해 중복이지만,
 * ErrorCode가 전송 계층(gRPC)을 모르게 한다는 원칙(컨벤션 8장)을 지키기 위해
 * 의도적으로 각 GrpcService가 따로 갖는다. support/ 공통 변환 계층(인터셉터/AOP)이
 * 생기면 이 중복은 해소된다 — 관련 제안 이슈 #{이슈번호}.</p>
 */
@Component
@RequiredArgsConstructor
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final IdempotencyExecutor idempotencyExecutor;
    private final OrderIdempotencyOperations idempotencyOperations;

    // ── 읽기 ──────────────────────────────────────────────────────────
    @Override
    public void listOrders(ListOrdersRequest request, StreamObserver<ListOrdersResponse> observer) {
        UUID actor = requireActor(request.getUserId());
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
        try {
            UUID actor = requireActor(request.getUserId());
            String idempotencyKey = currentIdempotencyKey();
            var command = new PlaceOrderCommand(
                    request.getSymbol(), toSide(request.getSide()), toKind(request.getKind()),
                    request.getQuantity(), request.getPrice(), idempotencyKey);

            PlaceOrderResponse response = idempotencyExecutor.execute(
                    request,
                    PlaceOrderResponse.parser(),
                    idempotencyOperations,
                    () -> PlaceOrderResponse.newBuilder()
                            .setOrder(toProto(orderService.placeOrder(actor, command)))
                            .build());

            observer.onNext(response);
            observer.onCompleted();
        } catch (OrderException e) {
            observer.onError(toGrpcException((OrderErrorCode) e.errorCode()));
        } catch (AccountException e) {
            observer.onError(toGrpcException((AccountErrorCode) e.errorCode()));
        }
    }

    @Override
    public void cancelOrder(CancelOrderRequest request, StreamObserver<CancelOrderResponse> observer) {
        try {
            UUID actor = requireActor(request.getUserId());

            CancelOrderResponse response = idempotencyExecutor.execute(
                    request,
                    CancelOrderResponse.parser(),
                    idempotencyOperations,
                    () -> {
                        CancelResult result = orderService.cancelOrder(actor, UUID.fromString(request.getOrderId()));
                        return CancelOrderResponse.newBuilder()
                                .setOrder(toProto(result.order()))
                                .setReleasedAmount(result.releasedAmount())
                                .build();
                    });

            observer.onNext(response);
            observer.onCompleted();
        } catch (OrderException e) {
            observer.onError(toGrpcException((OrderErrorCode) e.errorCode()));
        } catch (AccountException e) {
            observer.onError(toGrpcException((AccountErrorCode) e.errorCode()));
        }
    }

    // ── 예외 매핑 (임시 — support/ 공통 계층 도입 시 제거) ──────────────
    // OrderGrpcService가 자기 도메인 예외(OrderErrorCode)뿐 아니라
    // AccountService 호출로 발생하는 AccountErrorCode도 여기서 직접 매핑한다.
    // AccountGrpcService가 가진 매핑과 중복이지만, ErrorCode가 전송 계층(gRPC)을
    // 알게 하지 않는다는 원칙(컨벤션 8장)을 지키기 위한 선택이다 — 관련 제안 이슈 #{이슈번호}.
    private StatusRuntimeException toGrpcException(OrderErrorCode errorCode) {
        Status status = switch (errorCode) {
            case INVALID_QUANTITY, INVALID_PRICE, LIMIT_ORDER_REQUIRES_PRICE,
                 MARKET_ORDER_MUST_NOT_HAVE_PRICE ->
                    Status.INVALID_ARGUMENT;
            case ORDER_NOT_FOUND ->
                    Status.NOT_FOUND;
            case DUPLICATE_PENDING_ORDER, ORDER_NOT_PENDING, MARKET_ORDER_CANNOT_BE_CANCELLED,
                 OUTSIDE_TRADING_HOURS ->
                    Status.FAILED_PRECONDITION;
        };
        return status.withDescription(errorCode.message()).asRuntimeException();
    }

    private StatusRuntimeException toGrpcException(AccountErrorCode errorCode) {
        Status status = switch (errorCode) {
            case INVALID_LOCK_AMOUNT, INVALID_RELEASE_AMOUNT ->
                    Status.INVALID_ARGUMENT;
            case ACCOUNT_NOT_FOUND ->
                    Status.NOT_FOUND;
            case INSUFFICIENT_LOCKED_BALANCE, INSUFFICIENT_AVAILABLE_BALANCE,
                 INSUFFICIENT_CASH_BALANCE, ACCOUNT_INACTIVE ->
                    Status.FAILED_PRECONDITION;
        };
        return status.withDescription(errorCode.message()).asRuntimeException();
    }

    // ── actor / idempotency 컨텍스트 ────────────────────────────────────
    private UUID requireActor(String requestUserId) {
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
        return UUID.fromString(actor);
    }

    private String currentIdempotencyKey() {
        IdempotencyContext context = IdempotencyContext.current();
        return context == null ? null : context.idempotencyKey();
    }

    // ── 매핑 ──────────────────────────────────────────────────────────
    private Order toProto(OrderEntity order) {
        Order.Builder builder = Order.newBuilder()
                .setId(order.getId().toString())
                .setUserId(order.getUserId().toString())
                .setSymbol(order.getSymbol())
                .setSide(toProtoSide(order.getSide()))
                .setKind(toProtoKind(order.getOrderKind()))
                .setQuantity(order.getQuantity())
                .setPrice(order.getPriceKrw() == null ? 0 : order.getPriceKrw())
                .setStatus(toProtoStatus(order.getStatus()))
                .setReservedAmount(order.getReservedAmountKrw());
        if (order.getParentOrderId() != null) {
            builder.setParentOrderId(order.getParentOrderId().toString());
        }
        Instant createdAt = order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now();
        builder.setCreatedAt(toTimestamp(createdAt));
        return builder.build();
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
            default -> throw Status.INVALID_ARGUMENT
                    .withDescription("즉시 주문에는 지원하지 않는 kind입니다")
                    .asRuntimeException();
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