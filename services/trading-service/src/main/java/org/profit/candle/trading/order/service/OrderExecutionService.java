package org.profit.candle.trading.order.service;

import org.profit.candle.trading.order.entity.OrderEntity;

import java.util.UUID;

public interface OrderExecutionService {

    /**
     * 시장가 즉시 체결. (EXE-001)
     * MarketPriceProvider로 현재가를 받아 그 가격으로 즉시 체결 처리한다.
     * 같은 트랜잭션 안에서 OrderEntity.fill(), ExecutionEntity 생성,
     * AccountEntity 잔고 정산(settleBuy/settleSell), OrderFilled 이벤트
     * 발행까지 모두 수행한다.
     *
     * <p>PENDING 지정가의 조건 체결(EXE-002)은 별도 메서드로 다룬다 — 이
     * 메서드는 "주문 접수 시점에 곧바로 체결"하는 시장가 전용 경로다.</p>
     */
    OrderEntity fillMarketOrder(UUID orderId);
}
