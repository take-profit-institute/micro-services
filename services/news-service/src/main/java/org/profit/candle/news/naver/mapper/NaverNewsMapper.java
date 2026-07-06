package org.profit.candle.news.naver.mapper;

import org.profit.candle.news.naver.dto.NaverNewsItem;
import org.profit.candle.news.naver.dto.NaverNewsItemResponse;
import org.profit.candle.news.naver.dto.NaverNewsSearchResponse;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class NaverNewsMapper {
    private NaverNewsMapper() {
        throw new AssertionError("Utility class");
    }

    public static NaverNewsSearchResult toResult(NaverNewsSearchResponse response) {
        List<NaverNewsItem> items = response.items() == null
                ? List.of()
                : response.items().stream()
                .map(NaverNewsMapper::toItem)
                .toList();

        return new NaverNewsSearchResult(
                response.total(),
                response.start(),
                response.display(),
                items
        );
    }

    private static NaverNewsItem toItem(NaverNewsItemResponse response) {
        return new NaverNewsItem(
                clean(response.title()),
                clean(response.originalLink()),
                clean(response.link()),
                clean(response.description()),
                parsePublishedAt(response.pubDate())
        );
    }

    private static Instant parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(value.trim(), Instant::from);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("<[^>]*>", "")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
