package org.profit.candle.market.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * wishlist-service 의 심볼 구독 수요 이벤트를 소비해 실시간 구독 집합을 맞춘다.
 *
 * activate/deactivate 는 {@link SubscriptionManager} 에서 멱등하므로 별도 processed_events 없이
 * 재적용해도 안전하다. 소비자 그룹을 부팅마다 새로 잡고(earliest) 토픽을 replay 하면 심볼별 순서가
 * 보존된 채 최신 상태로 수렴한다 — 재시작 시 활성 집합 자가 복구.
 *
 * @see docs/REALTIME_QUOTE_PIPELINE.md 6. 계약 ③
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WishlistSubscriptionConsumer {

    // wishlist-service WishlistEventType 과 계약상 일치해야 하는 값들
    static final String TOPIC = "wishlist.symbol-subscription.v1";
    private static final String ACTIVATED = "WishlistSymbolActivated";
    private static final String DEACTIVATED = "WishlistSymbolDeactivated";

    private final SubscriptionManager subscriptionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = TOPIC)
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = node.path("eventType").asText();
            String symbol = node.path("symbol").asText();
            if (symbol.isBlank()) {
                return;
            }
            switch (eventType) {
                case ACTIVATED -> subscriptionManager.activateWishlist(symbol);
                case DEACTIVATED -> subscriptionManager.deactivateWishlist(symbol);
                default -> log.warn("알 수 없는 wishlist 구독 이벤트 타입 {}", eventType);
            }
        } catch (RuntimeException e) {
            log.warn("wishlist 구독 이벤트 처리 실패", e);
        }
    }
}
