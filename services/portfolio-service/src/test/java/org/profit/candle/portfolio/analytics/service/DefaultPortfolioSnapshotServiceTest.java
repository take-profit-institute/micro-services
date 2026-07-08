package org.profit.candle.portfolio.analytics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.RecordDailySnapshotCommand;
import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotReader;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPortfolioSnapshotServiceTest {

    @Mock PortfolioSnapshotReader snapshotReader;
    @Mock PortfolioSnapshotInserter snapshotInserter;
    @InjectMocks DefaultPortfolioSnapshotService service;

    private static final String USER_ID = "user-1";
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 29);

    private RecordDailySnapshotCommand command(long totalAsset, long stockValue, long seedCapital) {
        return new RecordDailySnapshotCommand(USER_ID, TODAY, totalAsset, stockValue, seedCapital, "idem-1");
    }

    @Test
    void recordDailySnapshot_computesDailyProfitAndCumulativeReturn() {
        PortfolioSnapshotEntity prev = new PortfolioSnapshotEntity(
                USER_ID, TODAY.minusDays(1), 1_050_000, 800_000, 0, "5.00");
        when(snapshotReader.findLatestBefore(USER_ID, TODAY)).thenReturn(Optional.of(prev));
        when(snapshotInserter.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        // total 1_100_000, seed 1_000_000 → 누적 10.00%, 전일 1_050_000 대비 +50_000
        PortfolioSnapshotResult result = service.recordDailySnapshot(command(1_100_000, 850_000, 1_000_000));

        assertThat(result.dailyProfit()).isEqualTo(50_000);
        assertThat(result.cumulativeReturnRate()).isEqualTo("10.00");
        assertThat(result.totalAsset()).isEqualTo(1_100_000);
    }

    @Test
    void recordDailySnapshot_noPreviousSnapshot_dailyProfitIsZero() {
        when(snapshotReader.findLatestBefore(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(snapshotInserter.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        PortfolioSnapshotResult result = service.recordDailySnapshot(command(1_000_000, 0, 1_000_000));

        assertThat(result.dailyProfit()).isZero();
        assertThat(result.cumulativeReturnRate()).isEqualTo("0.00"); // total == seed
    }

    @Test
    void recordDailySnapshot_alreadyExistsForDate_upsertsLatestValues() {
        PortfolioSnapshotEntity prev = new PortfolioSnapshotEntity(
                USER_ID, TODAY.minusDays(1), 1_050_000, 800_000, 0, "5.00");
        when(snapshotReader.findLatestBefore(USER_ID, TODAY)).thenReturn(Optional.of(prev));
        when(snapshotInserter.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        PortfolioSnapshotResult result = service.recordDailySnapshot(command(1_100_000, 850_000, 1_000_000));

        assertThat(result.totalAsset()).isEqualTo(1_100_000);
        assertThat(result.stockValue()).isEqualTo(850_000);
        assertThat(result.dailyProfit()).isEqualTo(50_000);
        assertThat(result.cumulativeReturnRate()).isEqualTo("10.00");
        verify(snapshotInserter).upsert(any());
    }

    @Test
    void recordDailySnapshot_zeroSeedCapital_returnRateIsZero() {
        when(snapshotReader.findLatestBefore(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(snapshotInserter.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<PortfolioSnapshotEntity> captor = ArgumentCaptor.forClass(PortfolioSnapshotEntity.class);
        service.recordDailySnapshot(command(500_000, 400_000, 0));

        verify(snapshotInserter).upsert(captor.capture());
        assertThat(captor.getValue().cumulativeReturnRate()).isEqualTo("0.00");
    }

    @Test
    void listDailySnapshots_defaultPageSize_fetchesOneExtraAndReturnsNextToken() {
        List<PortfolioSnapshotEntity> rows = snapshots(101);
        when(snapshotReader.findDailySnapshotsAfterUserId(eq(TODAY), isNull(), eq(101))).thenReturn(rows);

        var result = service.listDailySnapshots(TODAY, 0, "");

        assertThat(result.snapshots()).hasSize(100);
        assertThat(result.snapshots().get(0).userId()).isEqualTo("user-001");
        assertThat(result.snapshots().get(0).totalAsset()).isEqualTo(1_000_001);
        assertThat(result.snapshots().get(0).cumulativeReturnRate()).isEqualTo("1.00");
        assertThat(result.nextPageToken()).isEqualTo("user-100");
    }

    @Test
    void listDailySnapshots_usesPageTokenAsLastUserId() {
        when(snapshotReader.findDailySnapshotsAfterUserId(TODAY, "user-100", 3))
                .thenReturn(snapshots(2, 101));

        var result = service.listDailySnapshots(TODAY, 2, "user-100");

        assertThat(result.snapshots()).extracting("userId")
                .containsExactly("user-101", "user-102");
        assertThat(result.nextPageToken()).isEmpty();
    }

    @Test
    void listDailySnapshots_rejectsPageSizeOverMax() {
        assertThatThrownBy(() -> service.listDailySnapshots(TODAY, 501, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page_size must be between 1 and 500");
    }

    private List<PortfolioSnapshotEntity> snapshots(int count) {
        return snapshots(count, 1);
    }

    private List<PortfolioSnapshotEntity> snapshots(int count, int firstUserNumber) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    int n = firstUserNumber + i;
                    return new PortfolioSnapshotEntity(
                            String.format("user-%03d", n),
                            TODAY,
                            1_000_000L + n,
                            800_000L + n,
                            n,
                            String.format("%d.00", n));
                })
                .toList();
    }
}
