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
import org.profit.candle.trading.support.TradingFeePolicy;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultOrderService implements OrderService {

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

        if (orderRepository.existsByAccountIdAndSymbolAndStatus(
                account.getId(), command.symbol(), OrderStatusValue.PENDING)) {
            throw new OrderException(OrderErrorCode.DUPLICATE_PENDING_ORDER);
        }

        long amount = command.price() * command.quantity();
        long fee = BigDecimal.valueOf(amount)
                .multiply(TradingFeePolicy.FEE_RATE)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
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

        outboxWriter.record(outboxOperations, "OrderPlaced", order.getId().toString(),
                new OrderPlacedPayload(
                        order.getId().toString(), userId.toString(), order.getSymbol(),
                        order.getSide().name(), order.getQuantity(),
                        order.getPriceKrw() == null ? 0 : order.getPriceKrw(), reservedAmountKrw));

        if (command.kind() == OrderKindValue.MARKET) {
            return orderExecutionService.fillMarketOrder(order.getId());
        }

        return order;
    }

    @Override
    @Transactional
    public OrderEntity placeOrderFromReservation(UUID userId, PlaceOrderCommand command) {
        // 거래시간 검증 없음 — 배치 트리거라 시간 무관
        // lockBalance 없음 — 예약 생성 시점에 이미 잠금됨
        // fillMarketOrder 없음 — OPEN+LIMIT이라 즉시 체결 불필요

        AccountEntity account = accountService.getAccount(userId);

        if (orderRepository.existsByAccountIdAndSymbolAndStatus(
                account.getId(), command.symbol(), OrderStatusValue.PENDING)) {
            throw new OrderException(OrderErrorCode.DUPLICATE_PENDING_ORDER);
        }

        // OPEN+LIMIT이라 항상 지정가 — priceKrw는 command.price() 그대로
        OrderEntity order = OrderEntity.place(
                userId, account.getId(), command.symbol(), command.side(), command.kind(),
                command.quantity(), command.price(), 0L, command.idempotencyKey());
        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderPlaced", order.getId().toString(),
                new OrderPlacedPayload(
                        order.getId().toString(), userId.toString(), order.getSymbol(),
                        order.getSide().name(), order.getQuantity(),
                        order.getPriceKrw() == null ? 0 : order.getPriceKrw(), 0L));

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
        OrderEntity original = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        long releasedAmount = original.getReservedAmountKrw();
        original.markCancelled();

        if (releasedAmount > 0 && original.getSide() == OrderSideValue.BUY) {
            accountService.releaseBalance(userId, releasedAmount);
        }
        orderRepository.save(original);

        outboxWriter.record(outboxOperations, "OrderCancelled", original.getId().toString(),
                new OrderCancelledPayload(original.getId().toString(), userId.toString(), releasedAmount));

        if (command.quantity() <= 0 || command.price() <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
        }

        long amount = command.price() * command.quantity();
        long fee = BigDecimal.valueOf(amount)
                .multiply(TradingFeePolicy.FEE_RATE)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
        long newReservedAmountKrw = 0;

        if (original.getSide() == OrderSideValue.BUY) {
            newReservedAmountKrw = amount + fee;
            accountService.lockBalance(userId, newReservedAmountKrw);
        }

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