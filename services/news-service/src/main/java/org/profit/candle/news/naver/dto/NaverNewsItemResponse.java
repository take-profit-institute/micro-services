package org.profit.candle.news.naver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NaverNewsItemResponse(
        String title,
        @JsonProperty("originallink")
        String originalLink,
        String link,
        String description,
        String pubDate
) {
}
