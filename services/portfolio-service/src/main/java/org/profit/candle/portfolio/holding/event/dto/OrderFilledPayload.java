package org.profit.candle.portfolio.holding.event.dto;

/**
 * trading-service가 발행하는 OrderFilled 이벤트 페이로드(발행측 계약과 필드명 일치).
 * 알 수 없는 필드(fee/tax/net 등)는 Jackson이 무시한다. 멱등 키는 orderId다(주문당 체결 1회).
 */
public record OrderFilledPayload(
        String orderId,
        String userId,
        String symbol,
        String side,            // "BUY" | "SELL"
        long executedPriceKrw,
        long executedQuantity
) {}
