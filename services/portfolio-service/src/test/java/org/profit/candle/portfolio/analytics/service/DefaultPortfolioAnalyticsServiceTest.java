package org.profit.candle.portfolio.analytics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.portfolio.analytics.dto.MonthlyReturnResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.dto.TradingStatsResult;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.market.MarketQuoteClient;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotReader;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.entity.SellOutcome;
import org.profit.candle.portfolio.holding.repository.HoldingReader;
import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;
import org.profit.candle.portfolio.holding.trade.repository.RealizedTradeReader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPortfolioAnalyticsServiceTest {

    @Mock HoldingReader holdingReader;
    @Mock PortfolioSnapshotReader snapshotReader;
    @Mock RealizedTradeReader realizedTradeReader;
    @Mock MarketQuoteClient marketQuoteClient;
    @InjectMocks DefaultPortfolioAnalyticsService service;

    private static final String USER_ID = "user-1";

    private HoldingEntity holding(String symbol, long qty, long avgPrice, String sector) {
        HoldingEntity h = new HoldingEntity(USER_ID, symbol, "", sector, "KOSPI");
        h.applyBuy(qty, avgPrice);
        return h;
    }

    /** realizedProfit 만 의미 있는 종목 (최대 수익/손실 종목 테스트용). */
    private HoldingEntity holdingWithRealized(String symbol, long realizedProfit) {
        HoldingEntity h = new HoldingEntity(USER_ID, symbol, "", "반도체", "KOSPI");
        h.applyBuy(10, 10_000);
        // 매도로 realizedProfit 생성: (exit - 10000) * 10 == realizedProfit
        h.applySell(10, 10_000 + realizedProfit / 10);
        return h;
    }

    private RealizedTradeEntity trade(long realizedProfit, Instant openedAt, Instant closedAt) {
        return new RealizedTradeEntity(USER_ID, "005930",
                new SellOutcome(10, 10_000, 11_000, realizedProfit, openedAt, closedAt));
    }

    private PortfolioSnapshotEntity snapshot(LocalDate date, long totalAsset, long stockValue,
                                              long dailyProfit, String rate) {
        return new PortfolioSnapshotEntity(USER_ID, date, totalAsset, stockValue, dailyProfit, rate);
    }

    // ─── getSummary ──────────────────────────────────────────────────────────

    @Test
    void getSummary_withActiveHoldings_computesTotalsCorrectly() {
        HoldingEntity h1 = holding("005930", 10, 75_000, "반도체"); // book=750000, stock=750000
        HoldingEntity h2 = holding("035720", 5, 100_000, "인터넷"); // book=500000, stock=500000
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(h1, h2));
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of(h1, h2));
        when(marketQuoteClient.currentPrices(List.of("005930", "035720")))
                .thenReturn(Map.of("005930", 75_000L, "035720", 100_000L));

        PortfolioSummaryResult result = service.getSummary(USER_ID);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.totalBookValue()).isEqualTo(1_250_000);
        assertThat(result.totalStockValue()).isEqualTo(1_250_000);
        assertThat(result.totalUnrealizedProfit()).isEqualTo(0);
        assertThat(result.holdingCount()).isEqualTo(2);
        assertThat(result.totalReturnRate()).isEqualTo("0.00");
    }

    @Test
    void getSummary_cachedPriceAboveAvg_showsPositiveUnrealizedProfit() {
        HoldingEntity h = holding("005930", 10, 75_000, "반도체");
        h.updateCachedPrice(85_000);
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(h));
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of(h));
        when(marketQuoteClient.currentPrices(List.of("005930")))
                .thenReturn(Map.of("005930", 90_000L));

        PortfolioSummaryResult result = service.getSummary(USER_ID);

        assertThat(result.totalStockValue()).isEqualTo(900_000);
        assertThat(result.totalUnrealizedProfit()).isEqualTo(150_000);
        assertThat(result.totalReturnRate()).isEqualTo("20.00"); // 150000/750000*100
    }

    @Test
    void getSummary_realizedProfitSummedFromAllHoldings() {
        // 비활성 종목도 realized profit 포함
        HoldingEntity active = holding("005930", 10, 75_000, "반도체");
        HoldingEntity sold = new HoldingEntity(USER_ID, "000660", "", "반도체", "KOSPI");
        sold.applyBuy(5, 80_000);
        sold.applySell(5, 90_000); // realized = 50000, active=false
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(active));
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of(active, sold));
        when(marketQuoteClient.currentPrices(List.of("005930")))
                .thenReturn(Map.of("005930", 75_000L));

        PortfolioSummaryResult result = service.getSummary(USER_ID);

        assertThat(result.totalRealizedProfit()).isEqualTo(50_000);
        assertThat(result.holdingCount()).isEqualTo(1); // active only
    }

    @Test
    void getSummary_noHoldings_returnsAllZeros() {
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of());
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of());
        when(marketQuoteClient.currentPrices(List.of())).thenReturn(Map.of());

        PortfolioSummaryResult result = service.getSummary(USER_ID);

        assertThat(result.totalBookValue()).isEqualTo(0);
        assertThat(result.totalStockValue()).isEqualTo(0);
        assertThat(result.holdingCount()).isEqualTo(0);
        assertThat(result.totalReturnRate()).isEqualTo("0.00");
    }

    // ─── getHistory ──────────────────────────────────────────────────────────

    @Test
    void getHistory_withSnapshots_returnsOrderedResultsAndPeriodStats() {
        LocalDate today = LocalDate.now();
        PortfolioSnapshotEntity s1 = snapshot(today.minusDays(2), 1_000_000, 800_000, 0, "0.00");
        PortfolioSnapshotEntity s2 = snapshot(today.minusDays(1), 1_050_000, 850_000, 50_000, "5.00");
        when(snapshotReader.findByUserIdAfterDate(eq(USER_ID), any())).thenReturn(List.of(s1, s2));

        PortfolioHistoryResult result = service.getHistory(USER_ID, 7);

        assertThat(result.snapshots()).hasSize(2);
        assertThat(result.snapshots().get(0).date()).isEqualTo(today.minusDays(2).toString());
        assertThat(result.snapshots().get(1).totalAsset()).isEqualTo(1_050_000);
        assertThat(result.periodProfit()).isEqualTo(50_000); // last - first
        assertThat(result.periodReturnRate()).isEqualTo("5.00");
    }

    @Test
    void getHistory_noSnapshots_returnsEmptyWithZeros() {
        when(snapshotReader.findByUserIdAfterDate(any(), any())).thenReturn(List.of());

        PortfolioHistoryResult result = service.getHistory(USER_ID, 30);

        assertThat(result.snapshots()).isEmpty();
        assertThat(result.periodReturnRate()).isEqualTo("0.00");
        assertThat(result.periodProfit()).isEqualTo(0);
    }

    // ─── getSectorBreakdown ──────────────────────────────────────────────────

    @Test
    void getSectorBreakdown_groupsBySectorAndSortsByBookValueDesc() {
        // 반도체: 2000000, 인터넷: 1000000, total: 3000000
        HoldingEntity h1 = holding("005930", 10, 100_000, "반도체"); // 1000000
        HoldingEntity h2 = holding("000660", 5, 200_000, "반도체");  // 1000000
        HoldingEntity h3 = holding("035720", 20, 50_000, "인터넷");  // 1000000
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(h1, h2, h3));
        when(marketQuoteClient.currentPrices(List.of("005930", "000660", "035720")))
                .thenReturn(Map.of("005930", 100_000L, "000660", 200_000L, "035720", 50_000L));

        List<SectorBreakdownResult> results = service.getSectorBreakdown(USER_ID);

        assertThat(results).hasSize(2);
        SectorBreakdownResult semiconductor = results.get(0); // sorted desc
        assertThat(semiconductor.sector()).isEqualTo("반도체");
        assertThat(semiconductor.bookValue()).isEqualTo(2_000_000);
        assertThat(semiconductor.count()).isEqualTo(2);
        assertThat(semiconductor.weight()).isEqualTo("66.67"); // 2000000/3000000*100
    }

    @Test
    void getSectorBreakdown_singleSector_weightIs100() {
        HoldingEntity h = holding("005930", 10, 75_000, "반도체");
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(h));
        when(marketQuoteClient.currentPrices(List.of("005930")))
                .thenReturn(Map.of("005930", 75_000L));

        List<SectorBreakdownResult> results = service.getSectorBreakdown(USER_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).weight()).isEqualTo("100.00");
    }

    @Test
    void getSectorBreakdown_noHoldings_returnsEmpty() {
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of());
        when(marketQuoteClient.currentPrices(List.of())).thenReturn(Map.of());

        assertThat(service.getSectorBreakdown(USER_ID)).isEmpty();
    }

    // ─── getTradingStats ─────────────────────────────────────────────────────

    @Test
    void getTradingStats_winRateExcludesBreakEven() {
        Instant now = Instant.now();
        // 3승 1패 1본전 → 승률 = 3/(3+1) = 75.00
        when(realizedTradeReader.findByUserId(USER_ID)).thenReturn(List.of(
                trade(1_000, now.minus(2, ChronoUnit.DAYS), now),
                trade(2_000, now.minus(2, ChronoUnit.DAYS), now),
                trade(3_000, now.minus(2, ChronoUnit.DAYS), now),
                trade(-1_000, now.minus(2, ChronoUnit.DAYS), now),
                trade(0, now.minus(2, ChronoUnit.DAYS), now)));
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of());

        TradingStatsResult result = service.getTradingStats(USER_ID);

        assertThat(result.tradeCount()).isEqualTo(5);
        assertThat(result.winCount()).isEqualTo(3);
        assertThat(result.lossCount()).isEqualTo(1);
        assertThat(result.winRate()).isEqualTo("75.00");
    }

    @Test
    void getTradingStats_avgHoldingDays_ignoresNullOpenedAt() {
        Instant now = Instant.now();
        // 보유기간 4일, 6일 → 평균 5.00. openedAt null 인 건 제외.
        when(realizedTradeReader.findByUserId(USER_ID)).thenReturn(List.of(
                trade(1_000, now.minus(4, ChronoUnit.DAYS), now),
                trade(1_000, now.minus(6, ChronoUnit.DAYS), now),
                trade(1_000, null, now)));
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of());

        TradingStatsResult result = service.getTradingStats(USER_ID);

        assertThat(result.avgHoldingDays()).isEqualTo("5.00");
    }

    @Test
    void getTradingStats_bestAndWorstSymbolByRealizedProfit() {
        when(realizedTradeReader.findByUserId(USER_ID)).thenReturn(List.of());
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of(
                holdingWithRealized("005930", 50_000),
                holdingWithRealized("000660", -30_000),
                holdingWithRealized("035720", 10_000)));

        TradingStatsResult result = service.getTradingStats(USER_ID);

        assertThat(result.bestSymbol()).isEqualTo("005930");
        assertThat(result.bestProfit()).isEqualTo(50_000);
        assertThat(result.worstSymbol()).isEqualTo("000660");
        assertThat(result.worstProfit()).isEqualTo(-30_000);
    }

    @Test
    void getTradingStats_noTrades_returnsZeros() {
        when(realizedTradeReader.findByUserId(USER_ID)).thenReturn(List.of());
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of());

        TradingStatsResult result = service.getTradingStats(USER_ID);

        assertThat(result.tradeCount()).isZero();
        assertThat(result.winRate()).isEqualTo("0.00");
        assertThat(result.avgHoldingDays()).isEqualTo("0.00");
        assertThat(result.bestSymbol()).isEmpty();
        assertThat(result.worstSymbol()).isEmpty();
    }

    // ─── getMonthlyReturns ───────────────────────────────────────────────────

    @Test
    void getMonthlyReturns_groupsByMonthAndComputesReturn() {
        // 5월: 1000000 → 1100000 (+10.00%), 6월: 1100000 → 1100000 (0.00%)
        when(snapshotReader.findByUserIdAfterDate(eq(USER_ID), any())).thenReturn(List.of(
                snapshot(LocalDate.of(2026, 5, 1), 1_000_000, 0, 0, "0.00"),
                snapshot(LocalDate.of(2026, 5, 31), 1_100_000, 0, 0, "10.00"),
                snapshot(LocalDate.of(2026, 6, 1), 1_100_000, 0, 0, "0.00"),
                snapshot(LocalDate.of(2026, 6, 15), 1_100_000, 0, 0, "0.00")));

        List<MonthlyReturnResult> results = service.getMonthlyReturns(USER_ID, 6);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).month()).isEqualTo("2026-05");
        assertThat(results.get(0).returnRate()).isEqualTo("10.00");
        assertThat(results.get(0).profit()).isEqualTo(100_000);
        assertThat(results.get(1).month()).isEqualTo("2026-06");
        assertThat(results.get(1).returnRate()).isEqualTo("0.00");
    }

    @Test
    void getMonthlyReturns_noSnapshots_returnsEmpty() {
        when(snapshotReader.findByUserIdAfterDate(eq(USER_ID), any())).thenReturn(List.of());

        assertThat(service.getMonthlyReturns(USER_ID, 12)).isEmpty();
    }
}
