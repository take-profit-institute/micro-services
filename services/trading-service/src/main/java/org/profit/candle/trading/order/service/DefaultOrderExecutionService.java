package org.profit.candle.trading.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.order.entity.ExecutionEntity;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.event.OrderFilledPayload;
import org.profit.candle.trading.order.event.OrderOutboxOperations;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.repository.ExecutionRepository;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultOrderExecutionService implements OrderExecutionService {

    private final OrderRepository orderRepository;
    private final ExecutionRepository executionRepository;
    private final AccountService accountService;
    private final MarketPriceProvider marketPriceProvider;
    private final OutboxWriter outboxWriter;
    private final OrderOutboxOperations outboxOperations;
    private final OrderLimitFillExecutor limitFillExecutor;
    private final Clock clock;

    @Override
    @Transactional
    public OrderEntity fillMarketOrder(UUID orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.pending()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }
        if (executionRepository.findByOrderId(orderId).isPresent()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }

        long currentPriceKrw = marketPriceProvider.getCurrentPriceKrw(order.getSymbol());

        // 시장가는 체결 로직을 직접 수행 — limitFillExecutor와 doFill 중복을 피하기 위해
        // 인라인으로 처리한다 (시장가는 단건이라 별도 Executor 오버헤드 불필요).
        long grossAmount = currentPriceKrw * order.getQuantity();
        long feeKrw = (long) (grossAmount * 0.00015);
        long taxKrw = order.getSide() == OrderSideValue.SELL
                ? (long) (grossAmount * 0.0018) : 0;
        long netAmountKrw = order.getSide() == OrderSideValue.BUY
                ? grossAmount + feeKrw
                : grossAmount - feeKrw - taxKrw;

        Instant now = Instant.now(clock);
        ExecutionEntity execution = ExecutionEntity.create(
                order.getId(), currentPriceKrw, order.getQuantity(), feeKrw, taxKrw, netAmountKrw, now);
        executionRepository.save(execution);

        if (order.getSide() == OrderSideValue.BUY) {
            accountService.settleBuy(order.getUserId(), order.getReservedAmountKrw(), netAmountKrw);
        } else {
            accountService.settleSell(order.getUserId(), netAmountKrw);
        }

        order.fill();
        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderFilled", order.getId().toString(),
                new OrderFilledPayload(order.getId().toString(), order.getUserId().toString(),
                        order.getSymbol(), order.getSide().name(), currentPriceKrw,
                        order.getQuantity(), feeKrw, taxKrw, netAmountKrw));
        return order;
    }

    @Override
    public int fillLimitOrdersIfConditionMet(String symbol, long currentPrice) {
        // 락 없는 후보 조회 — 건별로 limitFillExecutor가 락 잡고 재검증 후 처리.
        List<OrderEntity> candidates = orderRepository
                .findPendingLimitOrdersBySymbol(symbol, OrderStatusValue.PENDING);

        int count = 0;
        for (OrderEntity candidate : candidates) {
            // 1차 조건 필터 (락 없는 상태) — 락 획득 후 Executor 내부에서 재검증한다.
            boolean mayFill = switch (candidate.getSide()) {
                case BUY -> currentPrice <= candidate.getPriceKrw();
                case SELL -> currentPrice >= candidate.getPriceKrw();
            };
            if (!mayFill) continue;

            try {
                if (limitFillExecutor.fillIfConditionMet(candidate.getId(), currentPrice)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("지정가 조건 체결 실패 — orderId={}, symbol={}, price={}",
                        candidate.getId(), symbol, currentPrice, e);
            }
        }
        return count;
    }
}