package org.profit.candle.wishlist.market.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.profit.candle.wishlist.market.dto.QuoteTick;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuoteMessageParser {
    private final ObjectMapper objectMapper;

    public QuoteTick parse(String payload) {
        try {
            return objectMapper.readValue(payload, QuoteTick.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid quote payload", e);
        }
    }
}
