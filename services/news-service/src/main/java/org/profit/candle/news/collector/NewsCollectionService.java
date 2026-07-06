package org.profit.candle.news.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.news.naver.client.NaverNewsClient;
import org.profit.candle.news.naver.dto.NaverNewsSearchRequest;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;
import org.profit.candle.news.naver.exception.NaverNewsApiException;
import org.profit.candle.news.stock.StockClient;
import org.profit.candle.news.stock.StockSnapshot;
import org.profit.candle.news.target.entity.CollectionTarget;
import org.profit.candle.news.target.repository.CollectionTargetJpaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectionService {
    private static final int DISPLAY = 3;
    private static final int START = 1;
    private static final String SORT = "date";
    private static final int MESSAGE_LIMIT = 1_000;
    private static final String STOCK_LOOKUP_FAILED = "STOCK_LOOKUP_FAILED";

    private final CollectionTargetJpaRepository targetRepository;
    private final StockClient stockClient;
    private final NaverNewsClient naverNewsClient;
    private final NewsCollectionWriter collectionWriter;

    public NewsCollectionResult collectActiveTargets() {
        List<CollectionTarget> targets = targetRepository.findByActiveTrueOrderByPriorityAsc();
        int successCount = 0;
        int failCount = 0;
        List<String> failureMessages = new ArrayList<>();

        for (CollectionTarget target : targets) {
            try {
                StockSnapshot stock = stockClient.getStock(target.getStockCode());
                NaverNewsSearchResult searchResult = naverNewsClient.search(new NaverNewsSearchRequest(
                        stock.name(),
                        DISPLAY,
                        START,
                        SORT
                ));
                collectionWriter.saveArticlesForTarget(target.getStockCode(), stock.name(), searchResult);
                successCount++;
            } catch (RuntimeException e) {
                failCount++;
                String failureMessage = failureMessage(target.getStockCode(), e);
                failureMessages.add(failureMessage);
                log.warn("News collection target failed. {}", failureMessage);
                log.debug("News collection target failure detail", e);
            }
        }

        collectionWriter.recordLog(targets.size(), successCount, failCount, logMessage(failureMessages));
        return new NewsCollectionResult(targets.size(), successCount, failCount);
    }

    static String failureMessage(String stockCode, RuntimeException exception) {
        return "stock_code=%s, reason=%s".formatted(stockCode, failureReason(exception));
    }

    private static String failureReason(RuntimeException exception) {
        if (exception instanceof NaverNewsApiException naverException) {
            return naverException.reason().message();
        }
        return STOCK_LOOKUP_FAILED;
    }

    private static String logMessage(List<String> failureMessages) {
        if (failureMessages.isEmpty()) {
            return "collection completed";
        }
        String message = String.join(" | ", failureMessages);
        if (message.length() <= MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, MESSAGE_LIMIT);
    }
}
