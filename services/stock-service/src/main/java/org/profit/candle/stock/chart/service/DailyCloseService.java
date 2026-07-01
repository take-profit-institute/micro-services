package org.profit.candle.stock.chart.service;

import java.time.LocalDate;

public interface DailyCloseService {

    /**
     * {@code tradeDate} 의 아직 확정되지 않은 일봉들을 마감 처리(closed=true)하고 종가 이벤트를 outbox 에 기록한다.
     * 이미 마감된 캔들은 건너뛰므로 재실행에 안전하다(자연 멱등).
     *
     * @return 이번 호출에서 새로 마감한 캔들 수
     */
    int closeDaily(LocalDate tradeDate);
}
