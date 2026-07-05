package org.profit.candle.news.naver.dto;

import java.util.Locale;

public record NaverNewsSearchRequest(
        String query,
        int display,
        int start,
        String sort
) {
    public NaverNewsSearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        if (display < 1 || display > 100) {
            throw new IllegalArgumentException("display must be between 1 and 100");
        }
        if (start < 1 || start > 1000) {
            throw new IllegalArgumentException("start must be between 1 and 1000");
        }

        query = query.trim();
        sort = normalizeSort(sort);
    }

    private static String normalizeSort(String value) {
        if (value == null || value.isBlank()) {
            return "date";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!"sim".equals(normalized) && !"date".equals(normalized)) {
            throw new IllegalArgumentException("sort must be sim or date");
        }
        return normalized;
    }
}
