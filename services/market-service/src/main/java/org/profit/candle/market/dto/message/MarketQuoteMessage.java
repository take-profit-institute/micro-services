package org.profit.candle.market.dto.message;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Redis 채널 {@code market:quotes} 발행 계약.
 *
 * wishlist-service 의 {@code QuoteTick} 과 필드가 1:1로 맞아야 한다. ±5% 판정에 시가(openPrice)가
 * 필수라 반드시 채운다. 다형성 타입정보(@class) 없이 순수 JSON 문자열로 발행한다 —
 * 소비 측은 body 바이트를 그대로 파싱한다.
 *
 * @see docs/WISHLIST_SERVICE_DESIGN.md 5. Redis Pub/Sub 입력 계약
 * @see docs/REALTIME_QUOTE_PIPELINE.md 5. 계약 ②
 */
public record MarketQuoteMessage(
        String symbol,
        long price,
        long openPrice,
        String marketStatus,
        LocalDate tradingDate,
        Instant timestamp
) {
}
