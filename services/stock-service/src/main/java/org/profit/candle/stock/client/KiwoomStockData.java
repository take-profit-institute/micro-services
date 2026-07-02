package org.profit.candle.stock.client;

import java.time.LocalDate;

/** 키움 응답을 도메인 중립 형태로 정규화한 종목 기준정보. */
public record KiwoomStockData(
        String code,
        String name,
        String marketType,       // KOSPI / KOSDAQ
        String sector,
        Long marketCap,
        Long sharesOutstanding,
        LocalDate listedAt,
        String listingStatus) {   // LISTED / DELISTED / SUSPENDED
}
