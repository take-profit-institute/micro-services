package org.profit.candle.portfolio.analytics.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.dto.DailyPortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.ListDailyPortfolioSnapshotsResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.RecordDailySnapshotCommand;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DefaultPortfolioSnapshotService implements PortfolioSnapshotService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;

    private final PortfolioSnapshotReader snapshotReader;
    private final PortfolioSnapshotInserter snapshotInserter;

    // EOD 배치는 같은 거래일을 운영자가 재실행할 수 있다.
    // 따라서 (user_id, snapshot_date)가 이미 있으면 기존 스냅샷을 최신 계산값으로 upsert한다.
    @Override
    public PortfolioSnapshotResult recordDailySnapshot(RecordDailySnapshotCommand command) {
        return toResult(snapshotInserter.upsert(buildSnapshot(command)));
    }

    @Override
    @Transactional(readOnly = true)
    public ListDailyPortfolioSnapshotsResult listDailySnapshots(
            LocalDate snapshotDate,
            int pageSize,
            String pageToken
    ) {
        int normalizedPageSize = normalizePageSize(pageSize);
        String lastUserId = blankToNull(pageToken);
        var rows = snapshotReader.findDailySnapshotsAfterUserId(
                snapshotDate,
                lastUserId,
                normalizedPageSize + 1
        );

        boolean hasNext = rows.size() > normalizedPageSize;
        var pageRows = hasNext ? rows.subList(0, normalizedPageSize) : rows;
        String nextPageToken = hasNext ? pageRows.get(pageRows.size() - 1).userId() : "";

        return new ListDailyPortfolioSnapshotsResult(
                pageRows.stream()
                        .map(s -> new DailyPortfolioSnapshotResult(
                                s.userId(),
                                s.totalAsset(),
                                s.cumulativeReturnRate()))
                        .toList(),
                nextPageToken
        );
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

    private int normalizePageSize(int pageSize) {
        if (pageSize == 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page_size must be between 1 and 500");
        }
        return pageSize;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
