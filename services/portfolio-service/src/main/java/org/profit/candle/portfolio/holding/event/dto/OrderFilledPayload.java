package org.profit.candle.portfolio.holding.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * trading-service가 발행하는 OrderFilled 이벤트 페이로드(발행측 계약과 필드명 일치).
 * 발행측은 fee/tax/net 등 추가 필드를 함께 보내지만 portfolio 계산에는 불필요하므로 무시한다
 * ({@link JsonIgnoreProperties}). 멱등 키는 orderId다(주문당 체결 1회).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderFilledPayload(
        String orderId,
        String userId,
        String symbol,
        String side,            // "BUY" | "SELL"
        long executedPriceKrw,
        long executedQuantity
) {}
