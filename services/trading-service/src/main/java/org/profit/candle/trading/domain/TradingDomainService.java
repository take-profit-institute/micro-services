package org.profit.candle.trading.domain;

import io.grpc.Status;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.domain.entity.AccountBalanceEntity;
import org.profit.candle.trading.domain.entity.OrderEntity;
import org.profit.candle.trading.domain.repository.AccountBalanceRepository;
import org.profit.candle.trading.domain.repository.OrderRepository;
import org.profit.candle.trading.event.OutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 거래 도메인 로직. 메서드는 IdempotencyExecutor의 트랜잭션 안에서 호출되어
 * 상태 변경 + outbox 기록이 멱등성 record와 한 트랜잭션으로 commit된다.
 *
 * 레퍼런스 범위: BUY는 가용 잔고를 예약(reserve)하고 PENDING 주문 생성, CancelOrder는 예약 해제.
 * 체결/보유종목(holdings) 갱신은 도메인 후속 작업으로 남긴다.
 */
@Service
@RequiredArgsConstructor
public class TradingDomainService {

    private static final double FEE_RATE = 0.00015;

    private final OrderRepository orderRepository;
    private final AccountBalanceRepository balanceRepository;
    private final OutboxWriter outboxWriter;

    @Value("${trading.starting-cash:10000000}")
    private long startingCash;

    public record PlaceOrderCommand(String symbol, OrderSideValue side, OrderKindValue kind,
                                    long quantity, long price) {}

    public record CancelResult(OrderEntity order, long releasedAmount) {}

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
            AccountBalanceEntity balance = loadOrCreateBalance(actorId);
            reserved = amount + fee;
            if (balance.availableCash() < reserved) {
                throw Status.FAILED_PRECONDITION.withDescription("가용 금액이 부족합니다").asRuntimeException();
            }
            balance.reserve(reserved);
            balanceRepository.save(balance);
        }

        OrderEntity order = new OrderEntity(
                UUID.randomUUID().toString(), actorId, command.symbol(), command.side(), command.kind(),
                command.quantity(), command.price(), OrderStatusValue.PENDING, null, reserved);
        orderRepository.save(order);

        outboxWriter.record("OrderPlaced", order.id(), new OrderPlacedPayload(
                order.id(), actorId, order.symbol(), order.side().name(), order.quantity(), order.price(), reserved));
        return order;
    }

    public CancelResult cancelOrder(String actorId, String orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, actorId)
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("주문을 찾을 수 없습니다").asRuntimeException());
        if (order.status() != OrderStatusValue.PENDING) {
            throw Status.FAILED_PRECONDITION.withDescription("대기 중인 주문만 취소할 수 있습니다").asRuntimeException();
        }

        long released = order.reservedAmount();
        if (released > 0 && order.side() == OrderSideValue.BUY) {
            AccountBalanceEntity balance = loadOrCreateBalance(actorId);
            balance.releaseReservation(released);
            balanceRepository.save(balance);
        }
        order.markCancelled();
        orderRepository.save(order);

        outboxWriter.record("OrderCancelled", order.id(),
                new OrderCancelledPayload(order.id(), actorId, released));
        return new CancelResult(order, released);
    }

    public AccountBalanceEntity getBalance(String actorId) {
        return loadOrCreateBalance(actorId);
    }

    private AccountBalanceEntity loadOrCreateBalance(String actorId) {
        return balanceRepository.findById(actorId)
                .orElseGet(() -> balanceRepository.save(new AccountBalanceEntity(actorId, startingCash, 0)));
    }

    private record OrderPlacedPayload(String orderId, String userId, String symbol, String side,
                                      long quantity, long price, long reservedAmount) {}

    private record OrderCancelledPayload(String orderId, String userId, long releasedAmount) {}
}
