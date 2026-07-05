package org.profit.candle.trading.support.event;

/**
 * 아웃박스 eventType → Kafka 토픽 매핑.
 *
 * <p>기본 규칙은 애그리거트 prefix + eventType 이다. 다만 소비 계약이 이미 굳은 이벤트는
 * 해당 토픽명을 그대로 유지한다:</p>
 * <ul>
 *   <li>{@code OrderFilled} → {@code orderFilled} (portfolio {@code ConsumedTopics.TRADING_ORDER_FILLED})</li>
 *   <li>{@code ReservationConverted} → {@code trading.order.ReservationConverted} (기본 규칙과 동일)</li>
 *   <li>{@code ReservationDue}/{@code ReservationExecuted} → {@code trading.reservation.*} (기본 규칙과 동일)</li>
 * </ul>
 */
final class TradingOutboxTopics {

    private static final String ORDER_PREFIX = "trading.order";
    private static final String RESERVATION_PREFIX = "trading.reservation";
    private static final String ACCOUNT_PREFIX = "trading.account";

    /** portfolio 등 소비자가 이미 쓰는 계약 토픽. */
    private static final String ORDER_FILLED_TOPIC = "orderFilled";

    static String forOrderEvent(String eventType) {
        if ("OrderFilled".equals(eventType)) {
            return ORDER_FILLED_TOPIC;
        }
        return ORDER_PREFIX + "." + eventType;
    }

    static String forReservationEvent(String eventType) {
        return RESERVATION_PREFIX + "." + eventType;
    }

    static String forAccountEvent(String eventType) {
        return ACCOUNT_PREFIX + "." + eventType;
    }

    private TradingOutboxTopics() {
        throw new AssertionError("Utility class");
    }
}
