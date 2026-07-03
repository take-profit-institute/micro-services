package org.profit.candle.trading.order.service;

import org.profit.candle.trading.order.dto.AmendOrderCommand;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;

import java.util.UUID;

public interface OrderService {

    /**
     * 즉시 주문을 접수한다. (ORD-002/003)
     * BUY는 AccountService.lockBalance로 가용 가능 금액을 검증/선점한다 (ORD-006/007).
     * 동일 계좌·동일 종목 PENDING 주문이 있으면 거부한다 (ORD-009).
     * 정규장 외 시간이면 거부한다 (TIM-001/002).
     */
    OrderEntity placeOrder(UUID userId, PlaceOrderCommand command);

    /**
     * 사용자가 직접 주문을 취소한다. (CAN-001~004)
     * PENDING 상태인 지정가 주문만 취소 가능하다. BUY 주문이면 잠긴 금액을
     * AccountService.releaseBalance로 즉시 반환한다.
     */
    CancelResult cancelOrder(UUID userId, UUID orderId);

    /**
     * 주문을 정정한다. (CAN-005~008)
     * PENDING 상태인 지정가 주문만 정정 가능하다.
     * 정정 = 원주문 취소(reserved_balance 반환) + 신규 주문 생성(parent_order_id 설정).
     * 비관적 락으로 취소와의 동시성 충돌을 방지한다.
     */
    OrderEntity amendOrder(UUID userId, UUID orderId, AmendOrderCommand command);

    /**
     * 배치(스케줄러)가 호출하는 만료 취소. (RSV-014 일반화)
     * 정규장 마감(15:30)까지 미체결인 즉시 지정가 PENDING 주문을 시스템 권한으로
     * 취소한다 — userId 소유권 검증을 하지 않는다(배치는 본인 소유 여부를 모른다).
     * cancelOrder와 동일한 비관적 락/취소 로직을 재사용한다.
     */
    CancelResult cancelExpiredPendingOrder(UUID orderId);

    /**
     * batch-service(별도 배포 단위)의 ExpirePendingOrders gRPC가 호출하는 진입점.
     * PENDING 즉시 지정가 주문 전체를 대상으로 찾아 건별로 취소를 시도하고,
     * 실제로 취소된 건수를 반환한다. 다른 서비스가 OrderRepository/내부 메서드를
     * 직접 알 필요 없이 이 메서드 하나만 호출하면 된다 (컨벤션 1장: 서비스 간
     * Java 코드 직접 참조 금지).
     */
    int expirePendingOrders();
}