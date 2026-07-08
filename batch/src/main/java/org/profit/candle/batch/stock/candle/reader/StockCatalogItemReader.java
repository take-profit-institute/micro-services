package org.profit.candle.batch.stock.candle.reader;

import java.util.List;
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
    private final StockCandleRetryExecutor retryExecutor;
    private final int pageSize;

    private int page;
    private int index;
    private boolean finished;
    private List<String> current;
    private int totalPages = -1;

    public StockCatalogItemReader(
            StockCatalogClient catalogClient,
            StockCandleRetryExecutor retryExecutor,
            int pageSize
    ) {
        this.catalogClient = catalogClient;
        this.retryExecutor = retryExecutor;
        this.pageSize = pageSize;
    }

    @Override
    public String read() {
        while (!finished) {
            if (current == null) {
                int requestedPage = page;
                StockCatalogClient.Page loaded = retryExecutor.execute(
                        () -> catalogClient.listListedCodes(requestedPage, pageSize)
                );
                current = loaded.codes();
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
}
