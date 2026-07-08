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

import org.profit.candle.trading.support.TradingFeePolicy;

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
        BigDecimal fee = gross.multiply(TradingFeePolicy.FEE_RATE).setScale(0, RoundingMode.DOWN);
        BigDecimal tax = order.getSide() == OrderSideValue.SELL
                ? gross.multiply(TradingFeePolicy.TAX_RATE).setScale(0, RoundingMode.DOWN)
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
    @Transactional
    public OrderEntity fillReservationOrder(UUID orderId, long executedPrice) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.pending()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }
        if (executionRepository.findByOrderId(orderId).isPresent()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }

        long gross = executedPrice * order.getQuantity(); // overflow는 예약 배치 단계에서 이미 검증됨

        long feeKrw;
        long taxKrw;
        long netAmountKrw;
        if (order.getSide() == OrderSideValue.BUY) {
            // 예약 배치가 선점(lock)한 reserved_amount(=gross+fee)를 그대로 정산해 rounding 불일치를 없앤다.
            netAmountKrw = order.getReservedAmountKrw();
            feeKrw = Math.max(0, netAmountKrw - gross);
            taxKrw = 0;
        } else {
            feeKrw = BigDecimal.valueOf(gross).multiply(TradingFeePolicy.FEE_RATE).setScale(0, RoundingMode.DOWN).longValue();
            taxKrw = BigDecimal.valueOf(gross).multiply(TradingFeePolicy.TAX_RATE).setScale(0, RoundingMode.DOWN).longValue();
            netAmountKrw = gross - feeKrw - taxKrw;
        }

        Instant now = Instant.now(clock);
        ExecutionEntity execution = ExecutionEntity.create(
                order.getId(), executedPrice, order.getQuantity(), feeKrw, taxKrw, netAmountKrw, now);
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
                        order.getSymbol(), order.getSide().name(), executedPrice,
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

    @Override
    public void fillLimitOrderIfConditionMetOnPlacement(OrderEntity order) {
        long currentPriceKrw;
        try {
            currentPriceKrw = marketPriceProvider.getCurrentPriceKrw(order.getSymbol());
        } catch (OrderException e) {
            // 현재가 조회 실패(market-service 장애 등)로 접수 자체를 실패시키지 않는다.
            // PENDING 상태로 남겨두면 이후 tick 기반 EXE-002가 잡아준다.
            log.warn("접수 즉시 조건체결 확인 중 현재가 조회 실패 — orderId={}, symbol={}",
                    order.getId(), order.getSymbol(), e);
            return;
        }

        // BUY: 부른 값(price)이 현재가보다 높거나 같으면 즉시 매수 체결
        // SELL: 부른 값(price)이 현재가보다 낮거나 같으면 즉시 매도 체결
        boolean mayFill = switch (order.getSide()) {
            case BUY -> currentPriceKrw <= order.getPriceKrw();
            case SELL -> currentPriceKrw >= order.getPriceKrw();
        };
        if (!mayFill) {
            return;
        }

        // fillIfConditionMet은 락 재획득 후 조건을 다시 검증하고 체결한다. 같은 트랜잭션(같은
        // 영속성 컨텍스트) 안이라 findByIdForUpdate가 반환하는 인스턴스는 호출부가 들고 있는
        // order와 동일한 identity라서, 별도로 재조회하지 않아도 order.fill() 결과가 반영된다.
        limitFillExecutor.fillIfConditionMet(order.getId(), currentPriceKrw);
    }
}