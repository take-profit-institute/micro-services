package org.profit.candle.news.naver.dto;

import java.util.List;

public record NaverNewsSearchResult(
        int total,
        int start,
        int display,
        List<NaverNewsItem> items
) {
}
