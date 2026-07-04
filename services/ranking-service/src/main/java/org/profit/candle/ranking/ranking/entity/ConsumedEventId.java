package org.profit.candle.ranking.ranking.entity;

import java.io.Serializable;
import java.util.UUID;

public record ConsumedEventId(String sourceService, UUID eventId) implements Serializable {}
