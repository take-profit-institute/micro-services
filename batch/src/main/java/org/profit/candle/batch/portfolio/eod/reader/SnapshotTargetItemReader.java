package org.profit.candle.batch.portfolio.eod.reader;

import java.time.LocalDate;
import java.util.List;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.batch.portfolio.eod.policy.EodRetryExecutor;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

public class SnapshotTargetItemReader implements ItemStreamReader<SnapshotTarget> {

    private static final String PAGE_TOKEN_KEY = "portfolioEod.pageToken";
    private static final String PAGE_INDEX_KEY = "portfolioEod.pageIndex";
    private static final String FINISHED_KEY = "portfolioEod.finished";

    private final SnapshotTargetClient targetClient;
    private final EodRetryExecutor retryExecutor;
    private final LocalDate businessDate;
    private final int pageSize;

    private String pageToken = "";
    private int pageIndex;
    private boolean finished;
    private SnapshotTarget.Page currentPage;

    public SnapshotTargetItemReader(
            SnapshotTargetClient targetClient,
            EodRetryExecutor retryExecutor,
            LocalDate businessDate,
            int pageSize
    ) {
        this.targetClient = targetClient;
        this.retryExecutor = retryExecutor;
        this.businessDate = businessDate;
        this.pageSize = pageSize;
    }

    @Override
    public SnapshotTarget read() {
        while (!finished) {
            if (currentPage == null) {
                currentPage = retryExecutor.execute(
                        () -> targetClient.loadTargets(businessDate, pageToken, pageSize)
                );
            }

            List<SnapshotTarget> targets = currentPage.targets();
            if (pageIndex < targets.size()) {
                return targets.get(pageIndex++);
            }

            String nextPageToken = currentPage.nextPageToken();
            if (nextPageToken == null || nextPageToken.isBlank()) {
                finished = true;
                return null;
            }

            pageToken = nextPageToken;
            pageIndex = 0;
            currentPage = null;
        }
        return null;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        pageToken = executionContext.getString(PAGE_TOKEN_KEY, "");
        pageIndex = executionContext.getInt(PAGE_INDEX_KEY, 0);
        finished = executionContext.getInt(FINISHED_KEY, 0) == 1;
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putString(PAGE_TOKEN_KEY, pageToken);
        executionContext.putInt(PAGE_INDEX_KEY, pageIndex);
        executionContext.putInt(FINISHED_KEY, finished ? 1 : 0);
    }
}
