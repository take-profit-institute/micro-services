package org.profit.candle.news.naver.dto;

import java.time.Instant;

public record NaverNewsItem(
        String title,
        String originalLink,
        String link,
        String description,
        Instant publishedAt
) {
}
