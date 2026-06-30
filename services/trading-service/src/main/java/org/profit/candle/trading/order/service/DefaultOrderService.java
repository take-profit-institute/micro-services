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

import java.util.List;
import java.util.UUID;

/**
 * Order 도메인 업무 서비스. 메서드는 IdempotencyExecutor의 트랜잭션 안에서 호출되어
 * 상태 변경 + outbox 기록이 멱등성 record와 한 트랜잭션으로 commit된다.
 *
 * 레퍼런스 범위: BUY는 가용 잔고를 예약(reserve)하고 PENDING 주문 생성, CancelOrder는 예약 해제.
 * 시장가는 접수 직후 같은 트랜잭션 안에서 OrderExecutionService.fillMarketOrder를 호출해
 * 즉시 체결까지 끝낸다 (EXE-001). 지정가 조건 체결(EXE-002)은 별도 트리거(시세 변화 감시)로
 * 처리하며, 이 서비스는 PENDING 생성까지만 책임진다.
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
        // TIM-001/002: 정규장 외 시간에는 즉시 주문 자체를 거부한다.
        tradingHoursValidator.requireMarketOpen();

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

        // EXE-001: 시장가는 접수 즉시 체결. 같은 트랜잭션 안에서 처리해
        // "PENDING으로 잠깐 보였다가 FILLED로 바뀌는" 어중간한 상태가 외부에
        // 노출되지 않는다 (트랜잭션 commit 전까지는 외부에서 안 보임).
        if (command.kind() == OrderKindValue.MARKET) {
            return orderExecutionService.fillMarketOrder(order.getId());
        }

        return order;
    }

    @Override
    @Transactional
    public CancelResult cancelOrder(UUID userId, UUID orderId) {

        // 사용자의 취소와 배치의 15:30 자동취소가 같은 주문을 동시에 노릴 수 있어
        // 비관적 락으로 조회한다 (findByIdAndUserId가 아니라 ...ForUpdate).
        OrderEntity order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        return doCancel(order, userId);
    }

    @Override
    @Transactional
    public CancelResult cancelExpiredPendingOrder(UUID orderId) {
        // 배치(스케줄러) 전용 — userId 소유권 검증 없이 시스템 권한으로 처리한다.
        // RSV-014 일반화: 정규장 마감(15:30)까지 미체결인 모든 즉시 지정가 PENDING 주문 대상.
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        return doCancel(order, order.getUserId());
    }

    /**
     * 취소 공통 처리. 호출 측이 이미 알맞은 락(findByIdAndUserIdForUpdate 또는
     * findByIdForUpdate)으로 order를 가져온 뒤 위임해야 한다 — 이 메서드 자체는
     * 추가 조회를 하지 않는다.
     */
    private CancelResult doCancel(OrderEntity order, UUID userId) {

        long releasedAmount = order.getReservedAmountKrw();

        // markCancelled()가 PENDING/LIMIT 여부를 자체 검증한다 (CAN-001/002/003).
        // 사용자 취소와 배치 자동취소가 동시에 들어와도, 먼저 커밋된 트랜잭션이
        // 끝난 뒤 락을 잡은 두 번째 호출은 여기서 ORDER_NOT_PENDING으로 막힌다.
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
}