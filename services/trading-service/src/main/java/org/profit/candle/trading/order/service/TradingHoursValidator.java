package org.profit.candle.trading.order.service;

import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 즉시 주문 거래 시간 검증 (TIM-001/002).
 *
 * 정규장(09:00~15:30, KST) 외 시간에는 즉시 주문을 접수할 수 없다.
 * Entity에 시간 판단을 두지 않고 별도 컴포넌트로 분리해, 시간을 모킹한
 * 단위 테스트가 Clock 주입만으로 가능하게 한다.
 *
 * 공휴일/단축거래일 같은 거래일 캘린더 연동은 범위 밖이다 — 현재는
 * 매일 09:00~15:30을 정규장으로 간주한다.
 */

@Component
public class TradingHoursValidator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final Clock clock;

    public TradingHoursValidator(Clock clock) {
        this.clock = clock;
    }

    /** 정규장 시간이 아니면 {@code OrderException(OUTSIDE_TRADING_HOURS)}를 던진다. */
    public void requireMarketOpen() {
        LocalTime now = LocalTime.now(clock.withZone(KST));
        if (now.isBefore(MARKET_OPEN) || !now.isBefore(MARKET_CLOSE)) {
            throw new OrderException(OrderErrorCode.OUTSIDE_TRADING_HOURS);
        }
    }


}
