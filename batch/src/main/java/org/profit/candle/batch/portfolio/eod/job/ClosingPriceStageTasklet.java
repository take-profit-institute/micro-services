package org.profit.candle.batch.portfolio.eod.job;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.profit.candle.batch.portfolio.eod.client.ClosingPriceClient;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.batch.portfolio.eod.policy.EodRetryExecutor;
import org.profit.candle.batch.portfolio.eod.repository.ClosingPriceStageRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class ClosingPriceStageTasklet implements Tasklet {

    private final long jobInstanceId;
    private final LocalDate businessDate;
    private final int symbolBatchSize;
    private final SnapshotTargetClient targetClient;
    private final ClosingPriceClient closingPriceClient;
    private final ClosingPriceStageRepository priceRepository;
    private final EodRetryExecutor retryExecutor;

    public ClosingPriceStageTasklet(
            long jobInstanceId,
            LocalDate businessDate,
            int symbolBatchSize,
            SnapshotTargetClient targetClient,
            ClosingPriceClient closingPriceClient,
            ClosingPriceStageRepository priceRepository,
            EodRetryExecutor retryExecutor
    ) {
        this.jobInstanceId = jobInstanceId;
        this.businessDate = businessDate;
        this.symbolBatchSize = symbolBatchSize;
        this.targetClient = targetClient;
        this.closingPriceClient = closingPriceClient;
        this.priceRepository = priceRepository;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String pageToken = "";
        Set<String> stagedSymbols = new HashSet<>();

        do {
            String requestToken = pageToken;
            SnapshotTarget.Page page = retryExecutor.execute(
                    () -> targetClient.loadTargets(
                            businessDate,
                            requestToken,
                            symbolBatchSize
                    )
            );
            List<String> newSymbols = page.targets().stream()
                    .flatMap(target -> target.holdings().stream())
                    .map(SnapshotTarget.Holding::symbol)
                    .filter(stagedSymbols::add)
                    .toList();
            stageInBatches(newSymbols);
            pageToken = page.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        return RepeatStatus.FINISHED;
    }

    private void stageInBatches(List<String> symbols) {
        for (int start = 0; start < symbols.size(); start += symbolBatchSize) {
            int end = Math.min(start + symbolBatchSize, symbols.size());
            stage(symbols.subList(start, end));
        }
    }

    private void stage(List<String> symbols) {
        List<ClosingPrice> prices = retryExecutor.execute(
                () -> closingPriceClient.loadClosingPrices(businessDate, symbols)
        );
        priceRepository.upsertAll(jobInstanceId, businessDate, prices);
    }
}
