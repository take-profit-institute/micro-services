package org.profit.candle.batch.market.orderbook.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.market.orderbook.client.MarketOrderBookRefreshClient;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketOrderBookRefreshTasklet implements Tasklet {
    private final MarketOrderBookRefreshClient client;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (!client.isMarketOpen()) {
            log.info("[Market OrderBook] skipped. reason=MARKET_CLOSED");
            return RepeatStatus.FINISHED;
        }

        MarketOrderBookRefreshClient.Result result = client.refresh();
        log.info(
                "[Market OrderBook] target={}, success={}, fail={}, skipped={}, reason={}",
                result.targetCount(),
                result.successCount(),
                result.failCount(),
                result.skipped(),
                result.reason()
        );
        return RepeatStatus.FINISHED;
    }
}
