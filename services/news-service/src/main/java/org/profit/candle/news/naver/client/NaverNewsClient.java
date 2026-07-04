package org.profit.candle.news.naver.client;

import org.profit.candle.news.naver.dto.NaverNewsSearchRequest;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;

public interface NaverNewsClient {
    NaverNewsSearchResult search(NaverNewsSearchRequest request);
}
