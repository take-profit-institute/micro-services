package org.profit.candle.batch.stock.candle.reader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.client.StockCatalogClient;
import org.profit.candle.batch.stock.candle.policy.StockCandleRetryExecutor;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * SearchStocks(LISTED)를 page 단위로 훑어 종목코드를 하나씩 방출한다.
 * ExecutionContext에 page/index/finished를 저장해 재시작(restart)에 안전하다.
 */
public class StockCatalogItemReader implements ItemStreamReader<String> {

    private static final String PAGE_KEY = "stockCandle.page";
    private static final String INDEX_KEY = "stockCandle.index";
    private static final String FINISHED_KEY = "stockCandle.finished";

    private final StockCatalogClient catalogClient;
    private final CandleBackfillClient candleClient;
    private final StockCandleRetryExecutor retryExecutor;
    private final int pageSize;
    private final Instant targetOpenTime;

    private int page;
    private int index;
    private boolean finished;
    private List<String> current;
    private int totalPages = -1;

    public StockCatalogItemReader(
            StockCatalogClient catalogClient,
            CandleBackfillClient candleClient,
            StockCandleRetryExecutor retryExecutor,
            int pageSize,
            String businessDate,
            String zoneId
    ) {
        this.catalogClient = catalogClient;
        this.candleClient = candleClient;
        this.retryExecutor = retryExecutor;
        this.pageSize = pageSize;
        this.targetOpenTime = resolveTargetOpenTime(businessDate, zoneId);
    }

    @Override
    public String read() {
        while (!finished) {
            if (current == null) {
                int requestedPage = page;
                StockCatalogClient.Page loaded = retryExecutor.execute(
                        () -> catalogClient.listListedCodes(requestedPage, pageSize)
                );
                current = filterMissingDailyCandles(loaded.codes());
                totalPages = loaded.totalPages();
            }

            if (index < current.size()) {
                return current.get(index++);
            }

            // 현재 페이지 소진 → 다음 페이지로. totalPages가 상한(0-based page).
            page++;
            index = 0;
            current = null;
            if (totalPages >= 0 && page >= totalPages) {
                finished = true;
                return null;
            }
        }
        return null;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        page = executionContext.getInt(PAGE_KEY, 0);
        index = executionContext.getInt(INDEX_KEY, 0);
        finished = executionContext.getInt(FINISHED_KEY, 0) == 1;
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putInt(PAGE_KEY, page);
        executionContext.putInt(INDEX_KEY, index);
        executionContext.putInt(FINISHED_KEY, finished ? 1 : 0);
    }

    private List<String> filterMissingDailyCandles(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> existing = retryExecutor.execute(() -> candleClient.findExistingDailyCodes(codes, targetOpenTime));
        if (existing.isEmpty()) {
            return codes;
        }
        Set<String> existingSet = new HashSet<>(existing);
        return codes.stream()
                .filter(code -> !existingSet.contains(code))
                .toList();
    }

    private static Instant resolveTargetOpenTime(String businessDate, String zoneId) {
        LocalDate date = businessDate == null || businessDate.isBlank()
                ? LocalDate.now(ZoneId.of(zoneId))
                : LocalDate.parse(businessDate);
        // stock-service 일봉 open_time은 날짜를 UTC 자정으로 저장한다.
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
