package org.profit.candle.portfolio.analytics.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.analytics.dto.MonthlyReturnResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.dto.TradingStatsResult;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.market.MarketQuoteClient;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotReader;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.repository.HoldingReader;
import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;
import org.profit.candle.portfolio.holding.trade.repository.RealizedTradeReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultPortfolioAnalyticsService implements PortfolioAnalyticsService {

    private final HoldingReader holdingReader;
    private final PortfolioSnapshotReader snapshotReader;
    private final RealizedTradeReader realizedTradeReader;
    private final MarketQuoteClient marketQuoteClient;

    @Override
    @Transactional(readOnly = true)
    public PortfolioSummaryResult getSummary(String userId) {
        List<HoldingEntity> active = holdingReader.findActiveByUserId(userId);
        List<HoldingEntity> all = holdingReader.findByUserId(userId);
        Map<String, Long> currentPrices = currentPrices(active);

        long totalBookValue = active.stream().mapToLong(HoldingEntity::bookValue).sum();
        long totalStockValue = active.stream()
                .mapToLong(h -> h.quantity() * currentPrice(h, currentPrices)).sum();
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
        Map<String, Long> currentPrices = currentPrices(active);
        long totalMarketValue = active.stream()
                .mapToLong(h -> h.quantity() * currentPrice(h, currentPrices))
                .sum();

        return active.stream()
                .collect(Collectors.groupingBy(HoldingEntity::sector))
                .entrySet().stream()
                .map(entry -> {
                    long sectorValue = entry.getValue().stream()
                            .mapToLong(h -> h.quantity() * currentPrice(h, currentPrices))
                            .sum();
                    String weight = totalMarketValue > 0
                            ? String.format("%.2f", (double) sectorValue / totalMarketValue * 100)
                            : "0.00";
                    return new SectorBreakdownResult(entry.getKey(), weight, sectorValue, entry.getValue().size());
                })
                .sorted(Comparator.comparingLong(SectorBreakdownResult::bookValue).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TradingStatsResult getTradingStats(String userId) {
        List<RealizedTradeEntity> trades = realizedTradeReader.findByUserId(userId);

        int winCount = (int) trades.stream().filter(t -> t.realizedProfit() > 0).count();
        int lossCount = (int) trades.stream().filter(t -> t.realizedProfit() < 0).count();
        int decided = winCount + lossCount; // 본전(0) 거래는 분모에서 제외
        String winRate = decided > 0
                ? String.format("%.2f", (double) winCount / decided * 100)
                : "0.00";

        double[] holdingDays = trades.stream()
                .filter(t -> t.openedAt() != null)
                .mapToDouble(t -> Duration.between(t.openedAt(), t.closedAt()).toSeconds() / 86_400.0)
                .toArray();
        String avgHoldingDays = holdingDays.length > 0
                ? String.format("%.2f", Arrays.stream(holdingDays).average().orElse(0))
                : "0.00";

        // 최대 수익/손실 종목: 종목별 누적 실현손익 기준
        List<HoldingEntity> holdings = holdingReader.findByUserId(userId);
        HoldingEntity best = holdings.stream()
                .max(Comparator.comparingLong(HoldingEntity::realizedProfit)).orElse(null);
        HoldingEntity worst = holdings.stream()
                .min(Comparator.comparingLong(HoldingEntity::realizedProfit)).orElse(null);

        return new TradingStatsResult(
                userId, trades.size(), winCount, lossCount, winRate, avgHoldingDays,
                best != null ? best.symbol() : "", best != null ? best.realizedProfit() : 0L,
                worst != null ? worst.symbol() : "", worst != null ? worst.realizedProfit() : 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyReturnResult> getMonthlyReturns(String userId, int months) {
        LocalDate from = YearMonth.now().minusMonths(Math.max(months, 1) - 1).atDay(1);
        List<PortfolioSnapshotEntity> snapshots = snapshotReader.findByUserIdAfterDate(userId, from);

        // 월별 첫/마지막 스냅샷 추출 (snapshotDate 오름차순 가정)
        Map<YearMonth, List<PortfolioSnapshotEntity>> byMonth = snapshots.stream()
                .collect(Collectors.groupingBy(
                        s -> YearMonth.from(s.snapshotDate()), TreeMap::new, Collectors.toList()));

        return byMonth.entrySet().stream()
                .map(entry -> {
                    List<PortfolioSnapshotEntity> monthSnapshots = entry.getValue();
                    long first = monthSnapshots.getFirst().totalAsset();
                    long last = monthSnapshots.getLast().totalAsset();
                    long profit = last - first;
                    String returnRate = first > 0
                            ? String.format("%.2f", (double) profit / first * 100)
                            : "0.00";
                    return new MonthlyReturnResult(entry.getKey().toString(), returnRate, profit);
                })
                .toList();
    }

    private Map<String, Long> currentPrices(List<HoldingEntity> holdings) {
        return marketQuoteClient.currentPrices(holdings.stream()
                .map(HoldingEntity::symbol)
                .toList());
    }

    private long currentPrice(HoldingEntity holding, Map<String, Long> currentPrices) {
        return currentPrices.getOrDefault(holding.symbol(), holding.cachedCurrentPrice());
    }
}
