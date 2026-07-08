package org.profit.candle.trading.support.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code market:quotes} Redis Pub/Sub 채널의 payload(JSON 문자열)를 {@link MarketQuoteTick}으로
 * 역직렬화한다. wishlist-service의 {@code QuoteMessageParser}와 동일한 패턴.
 */
@Component
@RequiredArgsConstructor
public class MarketQuoteMessageParser {

    private final ObjectMapper objectMapper;

    public MarketQuoteTick parse(String payload) {
        try {
            return objectMapper.readValue(payload, MarketQuoteTick.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid quote payload", e);
        }
    }
}