package org.profit.candle.trading.order.service;

import org.profit.candle.trading.client.MarketSessionClient;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.springframework.stereotype.Component;

/**
 * 즉시 주문 거래 시간 검증 (TIM-001/002).
 *
 * <p>주말·공휴일·정규장 시간(09:00~15:30 KST) 판정은 market-service의
 * {@code MarketSession}(권위 소스)이 소유한다. 이 검증기는 자체 시간 계산을 두지
 * 않고 {@link MarketSessionClient}에 위임한다 — trading·BFF·market이 장 운영
 * 상태를 각자 다르게 판단하던 불일치를 없앤다.</p>
 */
@Component
public class TradingHoursValidator {

    private final MarketSessionClient marketSessionClient;

    public TradingHoursValidator(MarketSessionClient marketSessionClient) {
        this.marketSessionClient = marketSessionClient;
    }

    /**
     * 정규장 체결 가능 시간이 아니면 {@code OrderException(OUTSIDE_TRADING_HOURS)}를 던진다.
     * market-service가 응답하지 않으면 {@code StatusRuntimeException}이 전파돼 주문이
     * 체결되지 않는다(장 상태 미확인 시 fail-safe).
     */
    public void requireMarketOpen() {
        if (!marketSessionClient.isMarketOpen()) {
            throw new OrderException(OrderErrorCode.OUTSIDE_TRADING_HOURS);
        }
    }
}
