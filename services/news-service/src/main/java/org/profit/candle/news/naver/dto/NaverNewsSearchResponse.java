package org.profit.candle.news.naver.dto;

import java.util.List;

public record NaverNewsSearchResponse(
        String lastBuildDate,
        int total,
        int start,
        int display,
        List<NaverNewsItemResponse> items
) {
}
