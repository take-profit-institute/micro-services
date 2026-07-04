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
     */
    OrderEntity fillMarketOrder(UUID orderId);

    /**
     * 예약(시장가/종가) 배치가 확정한 체결가로 PENDING Order를 즉시 체결한다.
     * fillMarketOrder와 달리 현재가를 조회하지 않고 넘겨받은 executedPrice를 쓴다.
     * BUY는 예약 시점에 선점(lock)된 reserved_amount로 정산해 rounding 불일치를 없앤다.
     * 같은 트랜잭션에서 ExecutionEntity 생성, 잔고 정산, OrderFilled 발행까지 수행한다.
     */
    OrderEntity fillReservationOrder(UUID orderId, long executedPrice);

    /**
     * 지정가 조건 체결 (EXE-002). Market 현재가 이벤트 수신 시 호출.
     * symbol의 PENDING 지정가 주문 중 currentPrice로 체결 가능한 것들을 즉시 체결한다.
     *
     * <p>BUY: 지정가(price) >= currentPrice이면 체결 (현재가가 지정가 이하로 떨어짐)
     * SELL: 지정가(price) <= currentPrice이면 체결 (현재가가 지정가 이상으로 오름)</p>
     *
     * @return 체결된 주문 건수
     */
    int fillLimitOrdersIfConditionMet(String symbol, long currentPrice);
}