package org.profit.candle.batch.stock.candle.reader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.client.StockCatalogClient;
import org.profit.candle.batch.stock.candle.policy.StockCandleRetryExecutor;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * SearchStocks(LISTED)를 page 단위로 훑어 종목코드를 하나씩 방출한다.
 *
 * <p>이 리더는 멀티스레드 스텝({@code step.taskExecutor})에서 쓰인다. 멀티스레드 스텝은 워커
 * 스레드마다 자기 청크 루프를 돌리므로 {@code read()} 가 여러 스레드에서 <b>동시에</b> 호출된다.
 * 커서(page/index/current)는 반드시 동기화되어야 한다 — 그렇지 않으면 같은 페이지를 중복 조회하거나
 * ({@code index++} 경합) 종목이 누락/중복되고, 최악엔 IndexOutOfBounds 로 스텝이 죽는다.
 *
 * <p>커서를 ExecutionContext 에 저장하지 않는다(= saveState=false 규약). 멀티스레드 스텝에서
 * 저장된 커서는 "여기까지 커밋됐다"를 뜻하지 않는다 — 워커 A가 150번째까지 읽고 커밋하는 동안
 * 워커 B는 아직 50~100번을 처리 중일 수 있어, 그 커서로 재시작하면 종목이 통째로 누락된다.
 * 대신 재시작은 {@link #filterMissingDailyCandles} 가 이미 일봉이 적재된 종목을 걸러내므로
 * 커서 없이도 데이터 수준에서 이어받는다(처음부터 훑되 남은 종목만 처리).
 */
public class StockCatalogItemReader implements ItemStreamReader<String> {

    private final StockCatalogClient catalogClient;
    private final CandleBackfillClient candleClient;
    private final StockCandleRetryExecutor retryExecutor;
    private final int pageSize;
    private final Instant targetOpenTime;

    private int page;
    private int index;
    private boolean finished;
    private List<String> current;
    private int totalPages = -1;

    public StockCatalogItemReader(
            StockCatalogClient catalogClient,
            CandleBackfillClient candleClient,
            StockCandleRetryExecutor retryExecutor,
            int pageSize,
            String businessDate,
            String zoneId
    ) {
        this.catalogClient = catalogClient;
        this.candleClient = candleClient;
        this.retryExecutor = retryExecutor;
        this.pageSize = pageSize;
        this.targetOpenTime = resolveTargetOpenTime(businessDate, zoneId);
    }

    // 워커 스레드들이 동시에 호출한다. 페이지 조회(gRPC)가 락 안에서 일어나지만 100종목당 1회뿐이라
    // 병렬 백필의 처리량에는 영향이 없다.
    @Override
    public synchronized String read() {
        while (!finished) {
            if (current == null) {
                int requestedPage = page;
                StockCatalogClient.Page loaded = retryExecutor.execute(
                        () -> catalogClient.listListedCodes(requestedPage, pageSize)
                );
                current = filterMissingDailyCandles(loaded.codes());
                totalPages = loaded.totalPages();
            }

            if (index < current.size()) {
                return current.get(index++);
            }

            // 현재 페이지 소진 → 다음 페이지로. totalPages가 상한(0-based page).
            page++;
            index = 0;
            current = null;
            if (totalPages >= 0 && page >= totalPages) {
                finished = true;
                return null;
            }
        }
        return null;
    }

    /** 커서를 복원하지 않는다 — 클래스 주석 참고. 항상 첫 페이지부터 훑고 필터가 남은 종목만 남긴다. */
    @Override
    public void open(ExecutionContext executionContext) {
        // no-op
    }

    /** 커서를 저장하지 않는다(saveState=false) — 멀티스레드 스텝에서 커서 재시작은 종목 누락을 만든다. */
    @Override
    public void update(ExecutionContext executionContext) {
        // no-op
    }

    private List<String> filterMissingDailyCandles(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> existing = retryExecutor.execute(() -> candleClient.findExistingDailyCodes(codes, targetOpenTime));
        if (existing.isEmpty()) {
            return codes;
        }
        Set<String> existingSet = new HashSet<>(existing);
        return codes.stream()
                .filter(code -> !existingSet.contains(code))
                .toList();
    }

    private static Instant resolveTargetOpenTime(String businessDate, String zoneId) {
        LocalDate date = businessDate == null || businessDate.isBlank()
                ? LocalDate.now(ZoneId.of(zoneId))
                : LocalDate.parse(businessDate);
        // stock-service 일봉 open_time은 날짜를 UTC 자정으로 저장한다.
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
