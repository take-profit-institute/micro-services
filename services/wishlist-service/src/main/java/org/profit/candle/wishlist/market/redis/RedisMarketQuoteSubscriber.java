package org.profit.candle.wishlist.market.redis;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.wishlist.alert.service.PriceAlertService;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMarketQuoteSubscriber implements MessageListener {
    private final QuoteMessageParser parser;
    private final PriceAlertService priceAlertService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            priceAlertService.evaluate(parser.parse(payload));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid market quote payload for wishlist alert");
        } catch (RuntimeException e) {
            log.error("Wishlist quote processing failed", e);
        }
    }
}
