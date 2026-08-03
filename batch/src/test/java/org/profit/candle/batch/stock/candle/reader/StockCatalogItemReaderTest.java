package org.profit.candle.batch.stock.candle.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    void read_skipsCodesThatAlreadyHaveDailyCandleAtBusinessDate() {
        List<String> codes = List.of("000001", "000002", "000003");
        Instant targetOpenTime = Instant.parse("2026-07-08T00:00:00Z");
        when(catalogClient.listListedCodes(0, 100)).thenReturn(new StockCatalogClient.Page(codes, 1));
        when(candleClient.findExistingDailyCodes(codes, targetOpenTime)).thenReturn(List.of("000002"));
        StockCatalogItemReader reader = new StockCatalogItemReader(
                catalogClient,
                candleClient,
                new StockCandleRetryExecutor(),
                100,
                "2026-07-08",
                "Asia/Seoul"
        );

        reader.open(new ExecutionContext());

        assertThat(reader.read()).isEqualTo("000001");
        assertThat(reader.read()).isEqualTo("000003");
        assertThat(reader.read()).isNull();
    }

    /**
     * 멀티스레드 스텝에서는 워커들이 read()를 동시에 호출한다. 동기화가 없으면 같은 페이지를
     * 중복 조회하거나(=키움 예산 낭비) index++ 경합으로 종목이 누락/중복된다.
     */
    @Test
    void read_emitsEveryCodeExactlyOnceUnderConcurrentReaders() throws Exception {
        int pages = 4;
        int pageSize = 25;
        int workers = 8;
        List<String> expected = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < pageSize; i++) {
                codes.add(String.format("%06d", page * pageSize + i));
            }
            expected.addAll(codes);
            when(catalogClient.listListedCodes(page, pageSize))
                    .thenReturn(new StockCatalogClient.Page(codes, pages));
        }
        when(candleClient.findExistingDailyCodes(anyList(), any())).thenReturn(List.of());

        StockCatalogItemReader reader = new StockCatalogItemReader(
                catalogClient,
                candleClient,
                new StockCandleRetryExecutor(),
                pageSize,
                "2026-07-08",
                "Asia/Seoul"
        );
        reader.open(new ExecutionContext());

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Callable<List<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            tasks.add(() -> {
                List<String> drained = new ArrayList<>();
                for (String code = reader.read(); code != null; code = reader.read()) {
                    drained.add(code);
                }
                return drained;
            });
        }
        List<String> emitted = new ArrayList<>();
        try {
            for (Future<List<String>> future : pool.invokeAll(tasks)) {
                emitted.addAll(future.get());
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        Collections.sort(emitted);
        assertThat(emitted).containsExactlyElementsOf(expected);
        // 페이지를 중복 조회하지 않는다.
        for (int page = 0; page < pages; page++) {
            verify(catalogClient, times(1)).listListedCodes(page, pageSize);
        }
    }
}
