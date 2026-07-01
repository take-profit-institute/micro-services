package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.client.KiwoomCandleData;
import org.profit.candle.stock.client.KiwoomChartClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCandleBackfillServiceTest {

    @Mock KiwoomChartClient chartClient;
    @Mock CandleBackfillPersistence persistence;

    @Test
    void backfill_returnsZeroWhenClientHasNoData() {
        when(chartClient.fetchCandles("005930", CandleInterval.DAY_1, 100, null)).thenReturn(List.of());
        DefaultCandleBackfillService service = new DefaultCandleBackfillService(chartClient, persistence);

        assertThat(service.backfill("005930", CandleInterval.DAY_1, 100, null)).isZero();
        verify(persistence).upsertFetched(List.of());
    }

    @Test
    void backfill_fetchesOutsidePersistenceAndDelegatesUpsert() {
        Instant openTime = Instant.parse("2026-06-30T00:00:00Z");
        List<KiwoomCandleData> fetched = List.of(new KiwoomCandleData("005930", CandleInterval.DAY_1,
                openTime, 70000, 71000, 69000, 70500, 1000));
        Instant to = Instant.parse("2026-07-01T00:00:00Z");
        when(chartClient.fetchCandles("005930", CandleInterval.DAY_1, 100, to)).thenReturn(fetched);
        when(persistence.upsertFetched(fetched)).thenReturn(1);
        DefaultCandleBackfillService service = new DefaultCandleBackfillService(chartClient, persistence);

        int upserted = service.backfill("005930", CandleInterval.DAY_1, 100, to);

        assertThat(upserted).isEqualTo(1);
        verify(persistence).upsertFetched(fetched);
    }
}
