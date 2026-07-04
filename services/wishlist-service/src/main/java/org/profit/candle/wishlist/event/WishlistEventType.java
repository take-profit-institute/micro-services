package org.profit.candle.wishlist.event;

/**
 * wishlist-service 가 소유하는 이벤트 타입. wire name·topic·버전을 여기서 관리한다(공용 enum 없음).
 *
 * 심볼 실시간 구독 수요 이벤트는 한 토픽(key=symbol)으로 발행한다. market-service 가 소비해
 * 키움 실시간 구독 집합을 맞춘다. 토픽은 심볼별 log compaction 대상으로 두어, 소비자가 재시작 시
 * 최신 상태를 replay 로 복구한다.
 *
 * @see docs/REALTIME_QUOTE_PIPELINE.md 6. 계약 ③
 */
public enum WishlistEventType {
    SYMBOL_ACTIVATED("WishlistSymbolActivated"),
    SYMBOL_DEACTIVATED("WishlistSymbolDeactivated");

    public static final String TOPIC = "wishlist.symbol-subscription.v1";

    private final String wireName;

    WishlistEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public String topic() {
        return TOPIC;
    }
}
