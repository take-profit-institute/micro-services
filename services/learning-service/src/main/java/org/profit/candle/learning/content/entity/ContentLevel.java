package org.profit.candle.learning.content.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ContentLevel {

    BEGINNER("초급"),
    INTERMEDIATE("중급"),
    ADVANCED("고급");

    private final String dbValue;

    public static ContentLevel fromDbValue(String dbValue) {
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ContentLevel: " + dbValue));
    }
}