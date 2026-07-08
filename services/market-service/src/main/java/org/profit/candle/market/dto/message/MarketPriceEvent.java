package org.profit.candle.market.dto.message;

/**
 * trading-service가 지정가/시가 예약 체결에 사용하는 현재가 Kafka 이벤트 계약.
 *
 * JSON 필드명은 trading.support.event.MarketPriceEvent와 맞춘다:
 * {"symbol":"005930","price":71400}
 */
public record MarketPriceEvent(
        String symbol,
        long price
) {
}
