package org.profit.candle.trading.order.service;

import io.grpc.Status;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.event.OrderCancelledPayload;
import org.profit.candle.trading.order.event.OrderOutboxOperations;
import org.profit.candle.trading.order.event.OrderPlacedPayload;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;

/**
 * Order 도메인 업무 서비스. 메서드는 IdempotencyExecutor의 트랜잭션 안에서 호출되어
 * 상태 변경 + outbox 기록이 멱등성 record와 한 트랜잭션으로 commit된다.
 *
 * 레퍼런스 범위: BUY는 가용 잔고를 예약(reserve)하고 PENDING 주문 생성, CancelOrder는 예약 해제.
 * 체결/보유종목(holdings) 갱신은 도메인 후속 작업으로 남긴다.
 */
@Service
@RequiredArgsConstructor
public class DefaultOrderService implements OrderService {

    private static final double FEE_RATE = 0.00015;

    private final OrderRepository orderRepository;
    private final AccountService accountService;
    private final OutboxWriter outboxWriter;
    private final OrderOutboxOperations outboxOperations;

    @Override
    public OrderEntity placeOrder(String actorId, PlaceOrderCommand command) {
        if (command.quantity() <= 0 || command.price() <= 0) {
            throw Status.INVALID_ARGUMENT.withDescription("quantity와 price는 양수여야 합니다").asRuntimeException();
        }
        // ORD-009: 동일 종목 PENDING 주문 중복 방지
        if (orderRepository.existsByUserIdAndSymbolAndStatus(actorId, command.symbol(), OrderStatusValue.PENDING)) {
            throw Status.FAILED_PRECONDITION.withDescription("해당 종목에 이미 대기 중인 주문이 있습니다").asRuntimeException();
        }

        long amount = command.price() * command.quantity();
        long fee = Math.round(amount * FEE_RATE);
        long reserved = 0;

        if (command.side() == OrderSideValue.BUY) {
            reserved = amount + fee;
            accountService.reserveBalance(actorId, reserved);
        }

        OrderEntity order = new OrderEntity(
                UUID.randomUUID().toString(), actorId, command.symbol(), command.side(), command.kind(),
                command.quantity(), command.price(), OrderStatusValue.PENDING, null, reserved);
        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderPlaced", order.id(), new OrderPlacedPayload(
                order.id(), actorId, order.symbol(), order.side().name(), order.quantity(), order.price(), reserved));
        return order;
    }

    @Override
    public CancelResult cancelOrder(String actorId, String orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, actorId)
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("주문을 찾을 수 없습니다").asRuntimeException());
        if (order.status() != OrderStatusValue.PENDING) {
            throw Status.FAILED_PRECONDITION.withDescription("대기 중인 주문만 취소할 수 있습니다").asRuntimeException();
        }

        long released = order.reservedAmount();
        if (released > 0 && order.side() == OrderSideValue.BUY) {
            accountService.releaseBalance(actorId, released);
        }
        order.markCancelled();
        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderCancelled", order.id(),
                new OrderCancelledPayload(order.id(), actorId, released));
        return new CancelResult(order, released);
    }
}