package org.profit.candle.portfolio.analytics.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.RecordDailySnapshotCommand;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotReader;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultPortfolioSnapshotService implements PortfolioSnapshotService {

    private final PortfolioSnapshotReader snapshotReader;
    private final PortfolioSnapshotWriter snapshotWriter;

    @Override
    @Transactional
    public PortfolioSnapshotResult recordDailySnapshot(RecordDailySnapshotCommand command) {
        // (user_id, snapshot_date) UNIQUE 기반 멱등: 같은 날짜 재실행 시 기존 스냅샷을 그대로 반환
        return snapshotReader.findByUserIdAndDate(command.userId(), command.snapshotDate())
                .map(this::toResult)
                .orElseGet(() -> toResult(snapshotWriter.save(buildSnapshot(command))));
    }

    private PortfolioSnapshotEntity buildSnapshot(RecordDailySnapshotCommand command) {
        long dailyProfit = snapshotReader
                .findLatestBefore(command.userId(), command.snapshotDate())
                .map(prev -> command.totalAsset() - prev.totalAsset())
                .orElse(0L);

        String cumulativeReturnRate = command.seedCapital() > 0
                ? String.format("%.2f",
                        (double) (command.totalAsset() - command.seedCapital()) / command.seedCapital() * 100)
                : "0.00";

        return new PortfolioSnapshotEntity(
                command.userId(), command.snapshotDate(), command.totalAsset(),
                command.stockValue(), dailyProfit, cumulativeReturnRate);
    }

    private PortfolioSnapshotResult toResult(PortfolioSnapshotEntity entity) {
        return new PortfolioSnapshotResult(
                entity.snapshotDate().toString(), entity.totalAsset(),
                entity.stockValue(), entity.dailyProfit(), entity.cumulativeReturnRate());
    }
}
