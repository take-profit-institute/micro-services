package org.profit.candle.portfolio.analytics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotReader;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.repository.HoldingReader;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPortfolioAnalyticsServiceTest {

    @Mock HoldingReader holdingReader;
    @Mock PortfolioSnapshotReader snapshotReader;
    @InjectMocks DefaultPortfolioAnalyticsService service;

    private static final String USER_ID = "user-1";

    private HoldingEntity holding(String symbol, long qty, long avgPrice, String sector) {
        HoldingEntity h = new HoldingEntity(USER_ID, symbol, "", sector, "KOSPI");
        h.applyBuy(qty, avgPrice);
        return h;
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
        h.updateCachedPrice(85_000); // stock_value = 850000, book = 750000, unrealized = 100000
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of(h));
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of(h));

        PortfolioSummaryResult result = service.getSummary(USER_ID);

        assertThat(result.totalStockValue()).isEqualTo(850_000);
        assertThat(result.totalUnrealizedProfit()).isEqualTo(100_000);
        assertThat(result.totalReturnRate()).isEqualTo("13.33"); // 100000/750000*100
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

        PortfolioSummaryResult result = service.getSummary(USER_ID);

        assertThat(result.totalRealizedProfit()).isEqualTo(50_000);
        assertThat(result.holdingCount()).isEqualTo(1); // active only
    }

    @Test
    void getSummary_noHoldings_returnsAllZeros() {
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of());
        when(holdingReader.findByUserId(USER_ID)).thenReturn(List.of());

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

        List<SectorBreakdownResult> results = service.getSectorBreakdown(USER_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).weight()).isEqualTo("100.00");
    }

    @Test
    void getSectorBreakdown_noHoldings_returnsEmpty() {
        when(holdingReader.findActiveByUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.getSectorBreakdown(USER_ID)).isEmpty();
    }
}
