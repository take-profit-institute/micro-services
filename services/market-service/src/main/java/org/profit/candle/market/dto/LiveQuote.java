package org.profit.candle.market.dto;

import java.time.Instant;

/**
 * 뷰어 팬아웃용 라이브 시세(도메인). proto {@code LiveQuote} 와 대응하지만 WS 수신부가 proto 에
 * 의존하지 않도록 도메인 레코드로 둔다. 매핑은 {@code QuoteStreamBroker} 가 한다.
 */
public record LiveQuote(
        String symbol,
        long price,
        long change,
        double changeRate,
        long openPrice,
        long tradingVolume,
        String priceChangeSign,
        Instant timestamp
) {
}
