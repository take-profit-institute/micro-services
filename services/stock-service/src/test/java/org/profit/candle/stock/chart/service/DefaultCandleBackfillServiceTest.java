package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.chart.repository.CandleWriter;
import org.profit.candle.stock.client.KiwoomCandleData;
import org.profit.candle.stock.client.KiwoomChartClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCandleBackfillServiceTest {

    @Mock KiwoomChartClient chartClient;
    @Mock CandleReader candleReader;
    @Mock CandleWriter candleWriter;

    @Test
    void backfill_returnsZeroWhenClientHasNoData() {
        when(chartClient.fetchCandles("005930", CandleInterval.DAY_1, 100)).thenReturn(List.of());
        DefaultCandleBackfillService service = new DefaultCandleBackfillService(chartClient, candleReader, candleWriter);

        assertThat(service.backfill("005930", CandleInterval.DAY_1, 100)).isZero();

        verify(candleWriter, never()).saveAll(any());
    }

    @Test
    void backfill_upsertsFetchedCandles() {
        Instant openTime = Instant.parse("2026-06-30T00:00:00Z");
        when(chartClient.fetchCandles("005930", CandleInterval.DAY_1, 100))
                .thenReturn(List.of(new KiwoomCandleData("005930", CandleInterval.DAY_1,
                        openTime, 70000, 71000, 69000, 70500, 1000)));
        when(candleReader.findLatest("005930", "1d", null, 100)).thenReturn(List.of());
        DefaultCandleBackfillService service = new DefaultCandleBackfillService(chartClient, candleReader, candleWriter);

        int upserted = service.backfill("005930", CandleInterval.DAY_1, 100);

        assertThat(upserted).isEqualTo(1);
        ArgumentCaptor<Iterable<CandleEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(candleWriter).saveAll(captor.capture());
        CandleEntity saved = captor.getValue().iterator().next();
        assertThat(saved.id().stockCode()).isEqualTo("005930");
        assertThat(saved.close()).isEqualTo(70500);
    }
}
