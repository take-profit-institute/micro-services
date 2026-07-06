package org.profit.candle.learning.event;

public enum LearningEventType {

    LEARNING_COMPLETED("LearningCompleted", "learning.learning-completed.v1", 1);

    private final String wireName;
    private final String topic;
    private final int version;

    LearningEventType(String wireName, String topic, int version) {
        this.wireName = wireName;
        this.topic = topic;
        this.version = version;
    }

    public String wireName() { return wireName; }
    public String topic() { return topic; }
    public int version() { return version; }
}