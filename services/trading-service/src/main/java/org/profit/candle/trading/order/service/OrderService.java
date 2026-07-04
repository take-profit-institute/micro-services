package org.profit.candle.trading.order.service;

import org.profit.candle.trading.order.dto.AmendOrderCommand;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;

import java.util.UUID;

public interface OrderService {

    /**
     * 즉시 주문을 접수한다. (ORD-002/003)
     */
    OrderEntity placeOrder(UUID userId, PlaceOrderCommand command);

    /**
     * ReservationDue 이벤트 수신 시 Order 생성 (OPEN+LIMIT 예약 전환).
     * placeOrder()와 달리 거래시간 검증/lockBalance/즉시체결이 없다 —
     * 예약 생성 시점에 이미 처리됐기 때문이다.
     * Outbox(ReservationConverted) 기록을 같은 트랜잭션에서 처리한다 — 원자성 보장.
     *
     * @param reservedAmountKrw 예약 생성 시점에 이미 lockBalance된 금액
     * @param reservationId     전환 완료 후 ReservationConverted 이벤트에 담을 예약 ID
     */
    OrderEntity placeOrderFromReservation(UUID userId, PlaceOrderCommand command,
                                          long reservedAmountKrw, UUID reservationId);

    /**
     * ReservationExecuted 이벤트 수신 시(시장가/종가 예약이 확정 체결가로 실행됨),
     * 즉시 체결된 Order를 생성하고 계좌를 정산한 뒤 OrderFilled을 발행한다.
     * OPEN+LIMIT의 {@link #placeOrderFromReservation}과 대칭이며, 이 경로로 예약 체결이
     * 보유종목(portfolio)까지 반영된다.
     *
     * <p>멱등: reservationId 기반 idempotencyKey로 재수신을 흡수한다.</p>
     *
     * @param reservedAmount 예약 배치가 실행 시점에 선점(lock)한 금액(BUY만 > 0)
     */
    OrderEntity recordReservationFill(UUID userId, String symbol,
                                      org.profit.candle.trading.order.entity.OrderSideValue side,
                                      long quantity, long executedPrice,
                                      long reservedAmount, UUID reservationId);

    /**
     * 사용자가 직접 주문을 취소한다. (CAN-001~004)
     */
    CancelResult cancelOrder(UUID userId, UUID orderId);

    /**
     * 주문을 정정한다. (CAN-005~008)
     */
    OrderEntity amendOrder(UUID userId, UUID orderId, AmendOrderCommand command);

    /**
     * 배치(스케줄러)가 호출하는 만료 취소.
     */
    CancelResult cancelExpiredPendingOrder(UUID orderId);

    /**
     * batch-service의 ExpirePendingOrders gRPC가 호출하는 진입점.
     */
    int expirePendingOrders();
}