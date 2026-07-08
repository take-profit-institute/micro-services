package org.profit.candle.news.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.news.naver.client.NaverNewsClient;
import org.profit.candle.news.naver.dto.NaverNewsSearchRequest;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;
import org.profit.candle.news.naver.exception.NaverNewsApiException;
import org.profit.candle.news.naver.exception.NaverNewsApiFailureReason;
import org.profit.candle.news.stock.StockClient;
import org.profit.candle.news.stock.StockSnapshot;
import org.profit.candle.news.target.entity.CollectionTarget;
import org.profit.candle.news.target.repository.CollectionTargetJpaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.RATE_LIMITED;

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
    private final NewsCollectionProperties collectionProperties;
    private final NewsCollectionSleeper sleeper;

    public NewsCollectionResult collectActiveTargets() {
        return collectActiveTargets(null);
    }

    public NewsCollectionResult collectActiveTargets(String logMessagePrefix) {
        List<CollectionTarget> targets = targetRepository.findByActiveTrueOrderByPriorityAsc();
        int successCount = 0;
        int failCount = 0;
        int consecutiveRateLimitCount = 0;
        FailureSummary failureSummary = new FailureSummary();

        for (int batchStart = 0; batchStart < targets.size(); batchStart += collectionProperties.batchSize()) {
            int batchEnd = Math.min(batchStart + collectionProperties.batchSize(), targets.size());
            boolean batchStoppedByRateLimit = false;

            for (int index = batchStart; index < batchEnd; index++) {
                CollectionTarget target = targets.get(index);
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
                    consecutiveRateLimitCount = 0;
                } catch (RuntimeException e) {
                    failCount++;
                    FailureDetail failureDetail = failureDetail(target.getStockCode(), e);
                    failureSummary.add(failureDetail);
                    if (failureDetail.reason() == RATE_LIMITED) {
                        consecutiveRateLimitCount++;
                    } else {
                        consecutiveRateLimitCount = 0;
                    }
                    log.warn(
                            "News collection target failed. stock_code={}, reason={}, statusCode={}, naverErrorCode={}, responseBody={}",
                            failureDetail.stockCode(),
                            failureDetail.reasonMessage(),
                            failureDetail.statusCode(),
                            failureDetail.naverErrorCode(),
                            failureDetail.responseBodySnippet()
                    );
                    log.debug("News collection target failure detail", e);
                }

                if (shouldBackoff(consecutiveRateLimitCount)) {
                    log.warn(
                            "News collection batch stopped by consecutive rate limit. batchStart={}, batchEnd={}, consecutiveRateLimitCount={}, backoff={}",
                            batchStart,
                            batchEnd,
                            consecutiveRateLimitCount,
                            collectionProperties.rateLimitBackoff()
                    );
                    sleeper.sleep(collectionProperties.rateLimitBackoff());
                    consecutiveRateLimitCount = 0;
                    batchStoppedByRateLimit = true;
                    break;
                }
            }

            if (!batchStoppedByRateLimit && hasNextBatch(batchEnd, targets.size())) {
                sleeper.sleep(collectionProperties.requestDelay());
            }
        }

        collectionWriter.recordLog(targets.size(), successCount, failCount, logMessage(logMessagePrefix, failureSummary.message()));
        return new NewsCollectionResult(targets.size(), successCount, failCount);
    }

    static FailureDetail failureDetail(String stockCode, RuntimeException exception) {
        if (exception instanceof NaverNewsApiException naverException) {
            return new FailureDetail(
                    stockCode,
                    naverException.reason(),
                    naverException.statusCode(),
                    naverException.naverErrorCode(),
                    naverException.responseBodySnippet()
            );
        }
        return new FailureDetail(
                stockCode,
                null,
                null,
                null,
                null
        );
    }

    private boolean shouldBackoff(int consecutiveRateLimitCount) {
        return consecutiveRateLimitCount >= collectionProperties.maxConsecutiveRateLimit();
    }

    private static boolean hasNextBatch(int batchEnd, int targetCount) {
        return batchEnd < targetCount;
    }

    private static String logMessage(String prefix, String message) {
        if (prefix == null || prefix.isBlank()) {
            return message;
        }
        String prefixedMessage = prefix + " " + message;
        if (prefixedMessage.length() <= MESSAGE_LIMIT) {
            return prefixedMessage;
        }
        return prefixedMessage.substring(0, MESSAGE_LIMIT);
    }

    record FailureDetail(
            String stockCode,
            NaverNewsApiFailureReason reason,
            Integer statusCode,
            String naverErrorCode,
            String responseBodySnippet
    ) {
        private String message() {
            return "%s:%s:%s:%s".formatted(
                    stockCode,
                    reasonMessage(),
                    value(statusCode),
                    value(naverErrorCode)
            );
        }

        private String reasonMessage() {
            if (reason == null) {
                return STOCK_LOOKUP_FAILED;
            }
            return reason.message();
        }

        private static String value(Object value) {
            return value == null ? "-" : value.toString();
        }
    }

    private static final class FailureSummary {
        private static final int FIRST_FAILURE_LIMIT = 10;

        private int totalFailed;
        private int rateLimited;
        private final List<String> firstFailures = new ArrayList<>();

        private void add(FailureDetail failureDetail) {
            totalFailed++;
            if (failureDetail.reason() == RATE_LIMITED) {
                rateLimited++;
            }
            if (firstFailures.size() < FIRST_FAILURE_LIMIT) {
                firstFailures.add(failureDetail.message());
            }
        }

        private String message() {
            if (totalFailed == 0) {
                return "collection completed";
            }
            String message = "totalFailed=%d, rateLimited=%d, firstFailures=[%s]".formatted(
                    totalFailed,
                    rateLimited,
                    String.join(", ", firstFailures)
            );
            if (message.length() <= MESSAGE_LIMIT) {
                return message;
            }
            return message.substring(0, MESSAGE_LIMIT);
        }
    }
}
