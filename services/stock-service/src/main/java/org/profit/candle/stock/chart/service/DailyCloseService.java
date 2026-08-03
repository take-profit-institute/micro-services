package org.profit.candle.stock.chart.service;

import java.time.LocalDate;

public interface DailyCloseService {

    /**
     * {@code tradeDate} 의 아직 확정되지 않은 일봉들을 마감 처리(closed=true)하고,
     * 그날 일봉 전체에 대해 종가 이벤트를 outbox 에 기록한다.
     *
     * <p>이벤트 발행 대상을 closed=false 로 좁히지 않는다 — 적재 배치가 정규장 마감 이후에 돌면
     * 캔들이 이미 closed=true 로 들어와 발행 대상이 사라진다. 대신 outbox 의 결정적 eventId
     * (종목, 거래일 파생)로 중복을 막아 재실행에 안전하다.
     *
     * @return 이번 호출에서 새로 기록한 종가 이벤트 수(이미 기록된 종목은 세지 않는다)
     */
    int closeDaily(LocalDate tradeDate);
}
