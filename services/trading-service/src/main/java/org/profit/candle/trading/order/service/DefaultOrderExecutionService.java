package org.profit.candle.trading.order.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.order.entity.ExecutionEntity;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderSideValue;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultOrderExecutionService implements OrderExecutionService {

    private static final double FEE_RATE = 0.00015;
    private static final double TAX_RATE = 0.0018;

    private final OrderRepository orderRepository;
    private final ExecutionRepository executionRepository;
    private final AccountService accountService;
    private final MarketPriceProvider marketPriceProvider;
    private final OutboxWriter outboxWriter;
    private final OrderOutboxOperations outboxOperations;
    private final Clock clock;

    @Override
    @Transactional
    public OrderEntity fillMarketOrder(UUID orderId) {
        // 체결-잔고-주문상태를 같은 트랜잭션에서 묶기 위해 락을 걸고 조회한다.
        // (배치/이벤트 컨슈머가 같은 orderId를 중복 트리거해도 중복 체결을 막는다 —
        // EXE-006. orderRepository.findByIdForUpdate는 #52에서 이미 추가된 메서드.)
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.pending()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }

        // EXE-006: executions.order_id UNIQUE가 DB 최종 방어선. 애플리케이션
        // 레벨에서도 한 번 더 막아 의미 있는 에러 메시지를 준다.
        if (executionRepository.findByOrderId(orderId).isPresent()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }

        long currentPriceKrw = marketPriceProvider.getCurrentPriceKrw(order.getSymbol());

        long grossAmount = currentPriceKrw * order.getQuantity();

        long feeKrw = (long) (grossAmount * FEE_RATE); // 원 단위 미만 절사

        long taxKrw = order.getSide() == OrderSideValue.SELL ? (long) (grossAmount * TAX_RATE) : 0;

        long netAmountKrw = order.getSide() == OrderSideValue.BUY
                ? grossAmount + feeKrw
                : grossAmount - feeKrw - taxKrw;

        Instant now = Instant.now(clock);

        ExecutionEntity execution = ExecutionEntity.create(
                order.getId(), currentPriceKrw, order.getQuantity(), feeKrw, taxKrw, netAmountKrw, now
        );
        executionRepository.save(execution);

        // EXE-009/010: 체결 시 잔고 정산. AccountService가 락을 걸고 처리한다.
        if (order.getSide() == OrderSideValue.BUY) {
            accountService.settleBuy(order.getUserId(), order.getReservedAmountKrw(), netAmountKrw);
        } else {
            accountService.settleSell(order.getUserId(), netAmountKrw);
        }

        order.fill();
        orderRepository.save(order);

        outboxWriter.record(outboxOperations, "OrderFilled", order.getId().toString(),
                new OrderFilledPayload(order.getId().toString(), order.getUserId().toString(),
                        order.getSymbol(), order.getSide().name(), currentPriceKrw, order.getQuantity(), feeKrw, taxKrw, netAmountKrw));

        return order;
    }
}
