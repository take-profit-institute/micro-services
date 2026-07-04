package org.profit.candle.news.collector;

import lombok.RequiredArgsConstructor;
import org.profit.candle.news.article.entity.Article;
import org.profit.candle.news.article.repository.ArticleJpaRepository;
import org.profit.candle.news.log.entity.CollectionLog;
import org.profit.candle.news.log.entity.CollectionStatusType;
import org.profit.candle.news.log.repository.CollectionLogJpaRepository;
import org.profit.candle.news.mapping.repository.ArticleStockMappingJpaRepository;
import org.profit.candle.news.naver.dto.NaverNewsItem;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
class NewsCollectionWriter {
    private static final String SOURCE = "NAVER";

    private final ArticleJpaRepository articleRepository;
    private final ArticleStockMappingJpaRepository mappingRepository;
    private final CollectionLogJpaRepository logRepository;

    @Transactional
    public void saveArticlesForTarget(String stockCode, String stockName, NaverNewsSearchResult searchResult) {
        for (NaverNewsItem item : searchResult.items()) {
            if (!isRelatedToStock(item, stockName)) {
                continue;
            }

            String url = articleUrl(item);
            if (url == null || url.isBlank()) {
                continue;
            }

            Article article = saveOrLoadArticle(item, url);
            mappingRepository.insertIgnore(article.getId(), stockCode, stockName);
        }
    }

    @Transactional
    public void recordLog(int targetCount, int successCount, int failCount, String message) {
        logRepository.save(CollectionLog.create(
                targetCount,
                successCount,
                failCount,
                status(successCount, failCount),
                message
        ));
    }

    private Article saveOrLoadArticle(NaverNewsItem item, String url) {
        articleRepository.insertIgnore(
                item.title(),
                item.description(),
                url,
                SOURCE,
                item.publishedAt()
        );
        return articleRepository.findByUrl(url)
                .orElseThrow(() -> new NoSuchElementException("Inserted article was not found: " + url));
    }

    private static boolean isRelatedToStock(NaverNewsItem item, String stockName) {
        if (stockName == null || stockName.isBlank()) {
            return false;
        }

        String keyword = stockName.trim().toLowerCase(Locale.ROOT);
        return containsKeyword(item.title(), keyword) || containsKeyword(item.description(), keyword);
    }

    private static boolean containsKeyword(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static CollectionStatusType status(int successCount, int failCount) {
        if (failCount == 0) {
            return CollectionStatusType.success;
        }
        if (successCount == 0) {
            return CollectionStatusType.fail;
        }
        return CollectionStatusType.partial_fail;
    }

    private static String articleUrl(NaverNewsItem item) {
        if (item.originalLink() != null && !item.originalLink().isBlank()) {
            return item.originalLink();
        }
        return item.link();
    }
}
