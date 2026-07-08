package org.profit.candle.batch.stock.candle.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.client.StockCatalogClient;
import org.profit.candle.batch.stock.candle.policy.StockCandleRetryExecutor;
import org.springframework.batch.infrastructure.item.ExecutionContext;

@ExtendWith(MockitoExtension.class)
class StockCatalogItemReaderTest {

    @Mock StockCatalogClient catalogClient;
    @Mock CandleBackfillClient candleClient;

    @Test
    void read_skipsCodesThatAlreadyHaveDailyCandleAtTargetDate() {
        List<String> codes = List.of("000001", "000002", "000003");
        Instant targetOpenTime = Instant.parse("2026-07-09T00:00:00Z");
        when(catalogClient.listListedCodes(0, 100)).thenReturn(new StockCatalogClient.Page(codes, 1));
        when(candleClient.findExistingDailyCodes(codes, targetOpenTime)).thenReturn(List.of("000002"));
        StockCatalogItemReader reader = new StockCatalogItemReader(
                catalogClient,
                candleClient,
                new StockCandleRetryExecutor(),
                100,
                "2026-07-09",
                "Asia/Seoul"
        );

        reader.open(new ExecutionContext());

        assertThat(reader.read()).isEqualTo("000001");
        assertThat(reader.read()).isEqualTo("000003");
        assertThat(reader.read()).isNull();
    }
}
