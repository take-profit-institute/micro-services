package org.profit.candle.news.naver.mapper;

import org.junit.jupiter.api.Test;
import org.profit.candle.news.naver.dto.NaverNewsItem;
import org.profit.candle.news.naver.dto.NaverNewsItemResponse;
import org.profit.candle.news.naver.dto.NaverNewsSearchResponse;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NaverNewsMapperTest {
    @Test
    void shouldCleanHtmlAndParsePublishedAt() {
        NaverNewsSearchResult result = NaverNewsMapper.toResult(new NaverNewsSearchResponse(
                "Fri, 03 Jul 2026 10:00:00 +0900",
                1,
                1,
                1,
                List.of(new NaverNewsItemResponse(
                        "<b>Samsung</b> &amp; News",
                        "https://original.example.com/news",
                        "https://naver.example.com/news",
                        "Summary with <b>tag</b>",
                        "Fri, 03 Jul 2026 10:00:00 +0900"
                ))
        ));

        NaverNewsItem item = result.items().getFirst();

        assertThat(item.title()).isEqualTo("Samsung & News");
        assertThat(item.description()).isEqualTo("Summary with tag");
        assertThat(item.originalLink()).isEqualTo("https://original.example.com/news");
        assertThat(item.link()).isEqualTo("https://naver.example.com/news");
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-07-03T01:00:00Z"));
    }
}
