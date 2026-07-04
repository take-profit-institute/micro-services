package org.profit.candle.trading.client;

/**
 * market-service의 거래일/장 운영 상태 조회 클라이언트.
 *
 * <p>주말·공휴일·정규장 시간(09:00~15:30 KST) 판정은 market-service의
 * {@code MarketSession}(권위 소스)이 소유한다. trading은 즉시 주문 거래시간
 * 검증에서 이 값을 단일 소스로 사용한다 — 자체 시간 계산을 두지 않는다.</p>
 */
public interface MarketSessionClient {

    /** 지금 정규장 체결 가능 시간인가 (거래일 && 09:00~15:30 KST). */
    boolean isMarketOpen();

    /** 주어진 날짜가 거래일인가 (주말·휴장일 제외). 예약 실행 예정일 검증에 쓴다. */
    boolean isTradingDay(java.time.LocalDate date);
}
