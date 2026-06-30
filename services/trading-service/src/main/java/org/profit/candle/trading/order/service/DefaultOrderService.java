package org.profit.candle.trading.order.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.event.OrderCancelledPayload;
import org.profit.candle.trading.order.event.OrderOutboxOperations;
import org.profit.candle.trading.order.event.OrderPlacedPayload;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
    @Transactional
    public OrderEntity placeOrder(UUID userId, PlaceOrderCommand command) {
        if (command.quantity() <= 0 || command.price() <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
        }

        // account_id는 order_svc가 자체 보유하지 않는 값이라 매 호출 조회한다.
        // (크로스 스키마 FK 금지 — orders.account_id는 이 시점에 받아온 값을 그대로 저장)
        AccountEntity account = accountService.getAccount(userId);

        // ORD-009: 동일 종목 PENDING 주문 중복 방지
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
                order.getId().toString(), userId.toString(), order.getSymbol(), order.getSide().name(), order.getQuantity(), order.getPriceKrw() == null ? 0 : order.getPriceKrw(), reservedAmountKrw));
        return order;
    }

    @Override
    @Transactional
    public CancelResult cancelOrder(UUID userId, UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        long releasedAmount = order.getReservedAmountKrw();

        // markCancelled()가 PENDING/LIMIT 여부를 자체 검증한다 (CAN-001/002/003).
        order.markCancelled();

        // CAN-004: 취소 시 reserved_amount만큼 즉시 반환. SELL은 잔고를 잠그지 않으므로 반환 불필요.
        if (releasedAmount > 0 && order.getSide() == OrderSideValue.BUY) {
            accountService.releaseBalance(userId, releasedAmount);
        }

        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderCancelled", order.getId().toString(),
                new OrderCancelledPayload(order.getId().toString(), userId.toString(), releasedAmount));
        return new CancelResult(order, releasedAmount);
    }
}