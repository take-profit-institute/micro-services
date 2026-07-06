package org.profit.candle.market.session;

import java.time.LocalDate;

/**
 * 거래일 캘린더 조회. 휴장일(공휴일·임시휴장) 여부만 답한다.
 * 함수형 인터페이스라 단위 테스트에서 람다로 대체할 수 있다.
 */
@FunctionalInterface
public interface TradingCalendar {
    boolean isHoliday(LocalDate date);
}
