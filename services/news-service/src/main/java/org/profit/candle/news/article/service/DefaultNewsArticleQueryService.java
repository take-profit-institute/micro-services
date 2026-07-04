package org.profit.candle.news.article.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.news.article.dto.StockNewsArticleResult;
import org.profit.candle.news.article.dto.StockNewsResult;
import org.profit.candle.news.article.repository.ArticleJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultNewsArticleQueryService implements NewsArticleQueryService {
    private final ArticleJpaRepository articleRepository;

    @Override
    @Transactional(readOnly = true)
    public StockNewsResult getStockNews(String stockCode) {
        return new StockNewsResult(articleRepository.findTop3ByStockCode(stockCode).stream()
                .map(StockNewsArticleResult::from)
                .toList());
    }
}
