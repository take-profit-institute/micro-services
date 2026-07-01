package org.profit.candle.stock.event;

/** stock-service 가 소유하는 이벤트 타입. wire name·topic·버전을 이 enum 이 관리한다. */
public enum StockEventType {
    STOCK_DAILY_CLOSED("StockDailyClosed", "stock.daily-closed.v1", 1);

    private final String wireName;
    private final String topic;
    private final int version;

    StockEventType(String wireName, String topic, int version) {
        this.wireName = wireName;
        this.topic = topic;
        this.version = version;
    }

    public String wireName() {
        return wireName;
    }

    public String topic() {
        return topic;
    }

    public int version() {
        return version;
    }
}
