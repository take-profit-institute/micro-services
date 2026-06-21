package org.profit.candle.auth.event;

public enum AuthEventType {
    USER_CREATED("UserCreated", "auth.user-created.v1", 1);

    private final String wireName;
    private final String topic;
    private final int version;

    AuthEventType(String wireName, String topic, int version) {
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
