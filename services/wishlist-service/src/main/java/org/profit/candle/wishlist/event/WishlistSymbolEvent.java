package org.profit.candle.wishlist.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 심볼 실시간 구독 수요 이벤트 payload(JSON envelope).
 * 민감정보 없이 symbol 만 싣는다 — 소비자는 symbol 로 자기 상태만 갱신한다.
 */
public record WishlistSymbolEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        String symbol,
        Instant occurredAt
) {
    public static WishlistSymbolEvent of(WishlistEventType type, String symbol, Instant occurredAt) {
        return new WishlistSymbolEvent(UUID.randomUUID(), type.wireName(), 1, symbol, occurredAt);
    }
}
