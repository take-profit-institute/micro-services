package org.profit.candle.trading.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.profit.candle.trading.reservation.service.ReservationBatchExecutor;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 지정가 조건 체결(EXE-002)의 건별 트랜잭션 실행 단위.
 *
 * <p>{@link DefaultOrderExecutionService}에서 self-invocation으로 @Transactional을
 * 호출하면 Spring AOP 프록시를 우회해 트랜잭션이 적용되지 않는다.
 * {@link ReservationBatchExecutor}와 동일한 이유로 별도 @Service로 분리했다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLimitFillExecutor {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.00015");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.0018");

    private final OrderRepository orderRepository;
    private final ExecutionRepository executionRepository;
    private final AccountService accountService;
    private final OutboxWriter outboxWriter;
    private final OrderOutboxOperations outboxOperations;
    private final Clock clock;

    /**
     * 단일 지정가 주문 체결. 락 획득 → 조건 재검증 → 체결 처리.
     * 락 획득 시점에 이미 다른 트랜잭션이 상태를 바꿨으면 no-op.
     *
     * @return 체결 성공 여부
     */
    @Transactional
    public boolean fillIfConditionMet(UUID orderId, long currentPrice) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);
        if (order == null || !order.pending()) return false;
        if (executionRepository.findByOrderId(orderId).isPresent()) return false;

        // 락 획득 후 조건 재검증 — 캐시 조회와 락 획득 사이에 가격이 바뀔 수 있다.
        boolean conditionMet = switch (order.getSide()) {
            case BUY -> currentPrice <= order.getPriceKrw();
            case SELL -> currentPrice >= order.getPriceKrw();
        };
        if (!conditionMet) return false;

        doFill(order, currentPrice);
        return true;
    }

    void doFill(OrderEntity order, long currentPriceKrw) {
        BigDecimal gross = BigDecimal.valueOf(currentPriceKrw)
                .multiply(BigDecimal.valueOf(order.getQuantity()));
        BigDecimal fee = gross.multiply(FEE_RATE).setScale(0, RoundingMode.DOWN);
        BigDecimal tax = order.getSide() == OrderSideValue.SELL
                ? gross.multiply(TAX_RATE).setScale(0, RoundingMode.DOWN)
                : BigDecimal.ZERO;
        BigDecimal net = order.getSide() == OrderSideValue.BUY
                ? gross.add(fee)
                : gross.subtract(fee).subtract(tax);

        long grossAmount;
        long feeKrw;
        long taxKrw;
        long netAmountKrw;
        try {
            grossAmount = gross.longValueExact();
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
                new OrderFilledPayload(
                        order.getId().toString(), order.getUserId().toString(),
                        order.getSymbol(), order.getSide().name(), currentPriceKrw,
                        order.getQuantity(), feeKrw, taxKrw, netAmountKrw));
    }
}