package org.profit.candle.stock.catalog.dto;

/** 종목 조건검색. 각 필드가 비어있으면 해당 필터는 무시된다. */
public record StockSearchCriteria(String query, String market, String sector, String status) {

    /** 비어있는 문자열을 null 로 정규화한다. */
    public StockSearchCriteria {
        query = blankToNull(query);
        market = blankToNull(market);
        sector = blankToNull(sector);
        status = blankToNull(status);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
