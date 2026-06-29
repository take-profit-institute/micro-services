package org.profit.candle.portfolio.analytics.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotReader;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.repository.HoldingReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultPortfolioAnalyticsService implements PortfolioAnalyticsService {

    private final HoldingReader holdingReader;
    private final PortfolioSnapshotReader snapshotReader;

    @Override
    @Transactional(readOnly = true)
    public PortfolioSummaryResult getSummary(String userId) {
        List<HoldingEntity> active = holdingReader.findActiveByUserId(userId);
        List<HoldingEntity> all = holdingReader.findByUserId(userId);

        long totalBookValue = active.stream().mapToLong(HoldingEntity::bookValue).sum();
        long totalStockValue = active.stream()
                .mapToLong(h -> h.quantity() * h.cachedCurrentPrice()).sum();
        long totalUnrealizedProfit = totalStockValue - totalBookValue;
        long totalRealizedProfit = all.stream().mapToLong(HoldingEntity::realizedProfit).sum();

        String totalReturnRate = totalBookValue > 0
                ? String.format("%.2f", (double) totalUnrealizedProfit / totalBookValue * 100)
                : "0.00";

        // dayProfit / dayReturnRate: 전일 스냅샷 연동 필요 (현재는 0 반환)
        return new PortfolioSummaryResult(
                userId, totalBookValue, totalStockValue, totalUnrealizedProfit,
                totalRealizedProfit, totalReturnRate, "0.00", 0L, active.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioHistoryResult getHistory(String userId, int days) {
        LocalDate from = LocalDate.now().minusDays(Math.max(days, 1));
        List<PortfolioSnapshotEntity> snapshots = snapshotReader.findByUserIdAfterDate(userId, from);

        List<PortfolioSnapshotResult> results = snapshots.stream()
                .map(s -> new PortfolioSnapshotResult(
                        s.snapshotDate().toString(),
                        s.totalAsset(), s.stockValue(),
                        s.dailyProfit(), s.cumulativeReturnRate()))
                .toList();

        if (snapshots.isEmpty()) {
            return new PortfolioHistoryResult(results, "0.00", 0L);
        }

        long periodProfit = snapshots.getLast().totalAsset() - snapshots.getFirst().totalAsset();
        String periodReturnRate = snapshots.getLast().cumulativeReturnRate();

        return new PortfolioHistoryResult(results, periodReturnRate, periodProfit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectorBreakdownResult> getSectorBreakdown(String userId) {
        List<HoldingEntity> active = holdingReader.findActiveByUserId(userId);
        long totalBookValue = active.stream().mapToLong(HoldingEntity::bookValue).sum();

        return active.stream()
                .collect(Collectors.groupingBy(HoldingEntity::sector))
                .entrySet().stream()
                .map(entry -> {
                    long sectorValue = entry.getValue().stream().mapToLong(HoldingEntity::bookValue).sum();
                    String weight = totalBookValue > 0
                            ? String.format("%.2f", (double) sectorValue / totalBookValue * 100)
                            : "0.00";
                    return new SectorBreakdownResult(entry.getKey(), weight, sectorValue, entry.getValue().size());
                })
                .sorted(Comparator.comparingLong(SectorBreakdownResult::bookValue).reversed())
                .toList();
    }
}
