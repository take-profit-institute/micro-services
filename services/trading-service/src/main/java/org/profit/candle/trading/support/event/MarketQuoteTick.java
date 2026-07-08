package org.profit.candle.trading.support.event;

import java.time.Instant;
import java.time.LocalDate;

/**
 * market-service가 {@code market:quotes} 채널(Redis Pub/Sub)에 발행하는 현재가 payload.
 *
 * <p>wishlist-service의 {@code QuoteTick}과 동일한 구조를 그대로 미러링한다 — 같은 채널을
 * 구독하는 두 도메인이 각자 로컬 record로 파싱하며, market-service 원본 클래스를
 * 공유하지 않는다(서비스 경계 원칙). {@code MarketQuotePublisher}가 {@code @class} 없는
 * 순수 JSON으로 발행하므로 크로스 서비스 파싱에 문제가 없다.</p>
 */
public record MarketQuoteTick(
        String symbol,
        long price,
        long openPrice,
        String marketStatus,
        LocalDate tradingDate,
        Instant timestamp) {
}