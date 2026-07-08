package org.profit.candle.batch.stock.candle.client;

import java.util.List;

/** stock-service 카탈로그에서 LISTED 종목 코드를 페이지 단위로 열거한다. */
public interface StockCatalogClient {

    Page listListedCodes(int page, int size);

    /** codes = 이 페이지의 종목코드, totalPages = 전체 페이지 수(0-based page의 상한). */
    record Page(List<String> codes, int totalPages) {
    }
}
