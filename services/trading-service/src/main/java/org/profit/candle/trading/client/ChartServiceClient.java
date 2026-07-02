package org.profit.candle.trading.client;

import java.time.LocalDate;

/**
 * Stock 서비스의 ChartService gRPC를 호출하는 클라이언트 인터페이스.
 * 여러 도메인(reservation 배치 체결 등)이 공유하므로 trading.client 패키지에 둔다.
 */
public interface ChartServiceClient {

    /**
     * 종목 + 기준일자 기준으로 그 일자 직전의 마지막 일봉 종가(= 전 거래일 종가)를 조회한다.
     * GetPreviousCloseRequest.date에 기준일자 00:00 KST(UTC-9)를 넘긴다.
     *
     * <p>주말/공휴일을 감안해 마지막 거래일 종가를 반환하므로,
     * TODAY_CLOSE(당일종가) 체결 시에는 종가 확정(CloseDailyCandles) 후에 호출해야 한다.</p>
     *
     * @param symbol    종목코드 (예: "005930")
     * @param baseDate  기준일자 — 이 날짜보다 앞선 가장 최근 일봉 종가를 반환
     * @return 전 거래일 종가 (원 단위)
     */
    long getPreviousClose(String symbol, LocalDate baseDate);
}
