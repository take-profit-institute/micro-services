package org.profit.candle.stock.chart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.repository.CandleWriter;
import org.profit.candle.stock.client.KiwoomCandleData;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultCandleBackfillPersistenceTest {

    @Mock CandleWriter candleWriter;

    @Test
    void upsertFetched_mapsFetchedCandlesAndSavesInTransactionBoundary() {
        Instant openTime = Instant.parse("2026-06-30T00:00:00Z");
        DefaultCandleBackfillPersistence persistence = new DefaultCandleBackfillPersistence(candleWriter);

        int upserted = persistence.upsertFetched(List.of(new KiwoomCandleData("005930", CandleInterval.DAY_1,
                openTime, 70000, 71000, 69000, 70500, 1000)));

        assertThat(upserted).isEqualTo(1);
        ArgumentCaptor<Iterable<CandleEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(candleWriter).saveAll(captor.capture());
        CandleEntity saved = captor.getValue().iterator().next();
        assertThat(saved.id().stockCode()).isEqualTo("005930");
        assertThat(saved.id().interval()).isEqualTo("1d");
        assertThat(saved.close()).isEqualTo(70500);
    }
}
