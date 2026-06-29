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
import org.profit.candle.portfolio.analytics.repository.PortfolioSnapshotWriter;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPortfolioSnapshotServiceTest {

    @Mock PortfolioSnapshotReader snapshotReader;
    @Mock PortfolioSnapshotWriter snapshotWriter;
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
        when(snapshotReader.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(snapshotReader.findLatestBefore(USER_ID, TODAY)).thenReturn(Optional.of(prev));
        when(snapshotWriter.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // total 1_100_000, seed 1_000_000 → 누적 10.00%, 전일 1_050_000 대비 +50_000
        PortfolioSnapshotResult result = service.recordDailySnapshot(command(1_100_000, 850_000, 1_000_000));

        assertThat(result.dailyProfit()).isEqualTo(50_000);
        assertThat(result.cumulativeReturnRate()).isEqualTo("10.00");
        assertThat(result.totalAsset()).isEqualTo(1_100_000);
    }

    @Test
    void recordDailySnapshot_noPreviousSnapshot_dailyProfitIsZero() {
        when(snapshotReader.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(snapshotReader.findLatestBefore(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(snapshotWriter.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PortfolioSnapshotResult result = service.recordDailySnapshot(command(1_000_000, 0, 1_000_000));

        assertThat(result.dailyProfit()).isZero();
        assertThat(result.cumulativeReturnRate()).isEqualTo("0.00"); // total == seed
    }

    @Test
    void recordDailySnapshot_alreadyExistsForDate_isIdempotentAndDoesNotSave() {
        PortfolioSnapshotEntity existing = new PortfolioSnapshotEntity(
                USER_ID, TODAY, 1_200_000, 900_000, 30_000, "20.00");
        when(snapshotReader.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.of(existing));

        PortfolioSnapshotResult result = service.recordDailySnapshot(command(999_999, 1, 1_000_000));

        assertThat(result.totalAsset()).isEqualTo(1_200_000); // 기존 값 그대로
        assertThat(result.cumulativeReturnRate()).isEqualTo("20.00");
        verify(snapshotWriter, never()).save(any());
        verify(snapshotReader, never()).findLatestBefore(any(), any());
    }

    @Test
    void recordDailySnapshot_zeroSeedCapital_returnRateIsZero() {
        when(snapshotReader.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(snapshotReader.findLatestBefore(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(snapshotWriter.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<PortfolioSnapshotEntity> captor = ArgumentCaptor.forClass(PortfolioSnapshotEntity.class);
        service.recordDailySnapshot(command(500_000, 400_000, 0));

        verify(snapshotWriter).save(captor.capture());
        assertThat(captor.getValue().cumulativeReturnRate()).isEqualTo("0.00");
    }
}
