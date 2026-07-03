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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultOrderExecutionService implements OrderExecutionService {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.00015");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.0018");

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

        BigDecimal gross = BigDecimal.valueOf(currentPriceKrw)
                .multiply(BigDecimal.valueOf(order.getQuantity()));
        BigDecimal fee = gross.multiply(FEE_RATE).setScale(0, RoundingMode.DOWN);
        BigDecimal tax = order.getSide() == OrderSideValue.SELL
                ? gross.multiply(TAX_RATE).setScale(0, RoundingMode.DOWN)
                : BigDecimal.ZERO;
        BigDecimal net = order.getSide() == OrderSideValue.BUY
                ? gross.add(fee)
                : gross.subtract(fee).subtract(tax);

        long feeKrw;
        long taxKrw;
        long netAmountKrw;
        try {
            feeKrw = fee.longValueExact();
            taxKrw = tax.longValueExact();
            netAmountKrw = net.longValueExact();
        } catch (ArithmeticException e) {
            throw new OrderException(OrderErrorCode.INVALID_QUANTITY, e);
        }

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
        // 락 없는 후보 조회 — Projection으로 id/side/priceKrw만 로드 (엔티티 전체 로딩 방지).
        List<OrderRepository.LimitOrderCandidate> candidates = orderRepository
                .findPendingLimitOrdersBySymbol(symbol, OrderStatusValue.PENDING);

        int count = 0;
        int failCount = 0;
        for (OrderRepository.LimitOrderCandidate candidate : candidates) {
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
                // 일시적 오류(DB 락 경합, 네트워크 등) — 실패 건 수집 후 루프 완료 시 재throw.
                // 재throw하면 컨슈머가 오프셋 커밋을 막고 Kafka 재시도를 유도한다.
                log.error("지정가 조건 체결 실패 — orderId={}, symbol={}, price={}",
                        candidate.getId(), symbol, currentPrice, e);
                failCount++;
            }
        }
        if (failCount > 0) {
            throw new RuntimeException(
                    "지정가 조건 체결 일부 실패 — symbol: " + symbol + ", failCount: " + failCount);
        }
        return count;
    }
}