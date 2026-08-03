package org.profit.candle.batch.stock.candle.writer;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.stock.candle.exception.CandleIngestFailureLimitExceededException;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * 백필은 processor가 stock-service에서 이미 커밋했으므로 writer는 집계 로깅과 실패 상한 판정만 한다
 * (0건 종목이 많으면 키움 응답이 비었다는 신호 — 관측용).
 *
 * <p>종목 단위 실패는 예외가 아니라 {@link CandleIngestResult#failed()} 로 도착한다. 누적 실패가
 * {@code failureLimit} 을 넘으면(= 대량 장애) 남은 종목까지 키움을 계속 때리지 않고 잡을 떨군다.
 * 스텝 스코프 빈이므로 카운터는 스텝 실행마다 초기화된다.
 */
@Slf4j
public class CandleIngestItemWriter implements ItemWriter<CandleIngestResult> {

    private final int failureLimit;
    // 멀티스레드 스텝이라 여러 워커가 동시에 write 한다.
    private final AtomicInteger failures = new AtomicInteger();

    public CandleIngestItemWriter(int failureLimit) {
        this.failureLimit = failureLimit;
    }

    @Override
    public void write(Chunk<? extends CandleIngestResult> chunk) {
        int upsertedTotal = 0;
        int emptySymbols = 0;
        int failedSymbols = 0;
        for (CandleIngestResult result : chunk) {
            if (result.failed()) {
                failedSymbols++;
                continue;
            }
            upsertedTotal += result.upserted();
            if (result.upserted() == 0) {
                emptySymbols++;
            }
        }
        int totalFailures = failures.addAndGet(failedSymbols);
        log.info(
                "[Stock Candle Ingest] chunk symbols={}, upsertedCandles={}, emptySymbols={}, "
                        + "failedSymbols={}, totalFailures={}",
                chunk.size(),
                upsertedTotal,
                emptySymbols,
                failedSymbols,
                totalFailures
        );
        if (totalFailures > failureLimit) {
            throw new CandleIngestFailureLimitExceededException(totalFailures, failureLimit);
        }
    }

    /** 관측/테스트용 — 이번 스텝에서 누적된 실패 종목 수. */
    public int failureCount() {
        return failures.get();
    }
}
