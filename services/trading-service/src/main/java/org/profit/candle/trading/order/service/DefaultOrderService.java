package org.profit.candle.trading.order.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.order.dto.AmendOrderCommand;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.event.OrderAmendedPayload;
import org.profit.candle.trading.order.event.OrderCancelledPayload;
import org.profit.candle.trading.order.event.OrderOutboxOperations;
import org.profit.candle.trading.order.event.OrderPlacedPayload;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Order 도메인 업무 서비스. 메서드는 IdempotencyExecutor의 트랜잭션 안에서 호출되어
 * 상태 변경 + outbox 기록이 멱등성 record와 한 트랜잭션으로 commit된다.
 */
@Service
@RequiredArgsConstructor
public class DefaultOrderService implements OrderService {

    private static final double FEE_RATE = 0.00015;

    private final OrderRepository orderRepository;
    private final AccountService accountService;
    private final OutboxWriter outboxWriter;
    private final OrderOutboxOperations outboxOperations;
    private final TradingHoursValidator tradingHoursValidator;
    private final OrderExecutionService orderExecutionService;

    @Override
    @Transactional
    public OrderEntity placeOrder(UUID userId, PlaceOrderCommand command) {
        tradingHoursValidator.requireMarketOpen();

        if (command.quantity() <= 0 || command.price() <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
        }

        AccountEntity account = accountService.getAccount(userId);

        if (orderRepository.existsByAccountIdAndSymbolAndStatus(account.getId(), command.symbol(), OrderStatusValue.PENDING)) {
            throw new OrderException(OrderErrorCode.DUPLICATE_PENDING_ORDER);
        }

        long amount = command.price() * command.quantity();
        long fee = Math.round(amount * FEE_RATE);
        long reservedAmountKrw = 0;

        if (command.side() == OrderSideValue.BUY) {
            reservedAmountKrw = amount + fee;
            accountService.lockBalance(userId, reservedAmountKrw);
        }

        Long priceKrw = command.kind() == OrderKindValue.LIMIT ? command.price() : null;

        OrderEntity order = OrderEntity.place(
                userId, account.getId(), command.symbol(), command.side(), command.kind(),
                command.quantity(), priceKrw, reservedAmountKrw, command.idempotencyKey());
        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderPlaced", order.getId().toString(), new OrderPlacedPayload(
                order.getId().toString(), userId.toString(), order.getSymbol(), order.getSide().name(),
                order.getQuantity(), order.getPriceKrw() == null ? 0 : order.getPriceKrw(), reservedAmountKrw));

        if (command.kind() == OrderKindValue.MARKET) {
            return orderExecutionService.fillMarketOrder(order.getId());
        }

        return order;
    }

    @Override
    @Transactional
    public CancelResult cancelOrder(UUID userId, UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return doCancel(order, userId);
    }

    @Override
    @Transactional
    public OrderEntity amendOrder(UUID userId, UUID orderId, AmendOrderCommand command) {
        // CAN-005: PENDING 지정가 주문만 정정 가능.
        // 비관적 락 — 정정과 취소/배치가 같은 주문을 동시에 노리는 경합 방지.
        OrderEntity original = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        // markCancelled()가 내부에서 PENDING/LIMIT 여부를 검증한다 (CAN-005).
        // — PENDING이 아니면 ORDER_NOT_PENDING, 지정가가 아니면 MARKET_ORDER_CANNOT_BE_CANCELLED
        long releasedAmount = original.getReservedAmountKrw();
        original.markCancelled();

        // CAN-004: 원주문 취소 시 reserved_balance 즉시 반환.
        if (releasedAmount > 0 && original.getSide() == OrderSideValue.BUY) {
            accountService.releaseBalance(userId, releasedAmount);
        }
        orderRepository.save(original);

        outboxWriter.record(outboxOperations, "OrderCancelled", original.getId().toString(),
                new OrderCancelledPayload(original.getId().toString(), userId.toString(), releasedAmount));

        // CAN-007: 신규 주문 생성.
        // 거래시간 검증은 생략 — 원주문이 이미 정규장 안에서 접수된 PENDING이라는 게
        // 정정 가능 전제 조건이므로, 별도로 다시 거는 건 불필요하다.
        if (command.quantity() <= 0 || command.price() <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
        }

        long amount = command.price() * command.quantity();
        long fee = Math.round(amount * FEE_RATE);
        long newReservedAmountKrw = 0;

        if (original.getSide() == OrderSideValue.BUY) {
            newReservedAmountKrw = amount + fee;
            accountService.lockBalance(userId, newReservedAmountKrw);
        }

        // CAN-008: 신규 주문에 parent_order_id = 원주문 ID.
        OrderEntity amended = OrderEntity.placeWithParent(
                userId, original.getAccountId(), original.getSymbol(), original.getSide(),
                OrderKindValue.LIMIT, command.quantity(), command.price(),
                newReservedAmountKrw, command.idempotencyKey(), original.getId());
        orderRepository.save(amended);

        outboxWriter.record(outboxOperations, "OrderAmended", amended.getId().toString(),
                new OrderAmendedPayload(amended.getId().toString(), original.getId().toString(),
                        userId.toString(), original.getSymbol(), amended.getQuantity(),
                        amended.getPriceKrw() == null ? 0L : amended.getPriceKrw(), newReservedAmountKrw));

        return amended;
    }

    @Override
    @Transactional
    public CancelResult cancelExpiredPendingOrder(UUID orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return doCancel(order, order.getUserId());
    }

    @Override
    public int expirePendingOrders() {
        List<UUID> targets = orderRepository.findIdsByStatus(OrderStatusValue.PENDING);
        int cancelledCount = 0;
        for (UUID orderId : targets) {
            try {
                cancelExpiredPendingOrder(orderId);
                cancelledCount++;
            } catch (OrderException e) {
                // 이미 처리됨, 정상 스킵
            }
        }
        return cancelledCount;
    }

    private CancelResult doCancel(OrderEntity order, UUID userId) {
        long releasedAmount = order.getReservedAmountKrw();
        order.markCancelled();

        if (releasedAmount > 0 && order.getSide() == OrderSideValue.BUY) {
            accountService.releaseBalance(userId, releasedAmount);
        }

        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderCancelled", order.getId().toString(),
                new OrderCancelledPayload(order.getId().toString(), userId.toString(), releasedAmount));
        return new CancelResult(order, releasedAmount);
    }
}