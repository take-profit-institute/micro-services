package org.profit.candle.wishlist.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.wishlist.event.entity.OutboxEvent;
import org.profit.candle.wishlist.event.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 심볼 구독 수요 이벤트를 outbox 에 기록한다. wishlist_items 변경과 같은 트랜잭션에서 커밋되어야
 * at-least-once 발행이 보장된다(호출자의 @Transactional 안에서 호출한다).
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void recordSymbolActivated(String symbol) {
        record(WishlistEventType.SYMBOL_ACTIVATED, symbol);
    }

    public void recordSymbolDeactivated(String symbol) {
        record(WishlistEventType.SYMBOL_DEACTIVATED, symbol);
    }

    private void record(WishlistEventType type, String symbol) {
        Instant now = Instant.now();
        WishlistSymbolEvent event = WishlistSymbolEvent.of(type, symbol, now);
        outboxEventRepository.save(new OutboxEvent(
                event.eventId(),
                event.eventType(),
                symbol,
                objectMapper.writeValueAsString(event),
                now));
    }
}
