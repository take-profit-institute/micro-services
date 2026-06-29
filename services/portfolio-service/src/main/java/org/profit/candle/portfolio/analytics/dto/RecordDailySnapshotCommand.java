package org.profit.candle.portfolio.analytics.dto;

import java.time.LocalDate;

/**
 * 일별 스냅샷 기록 명령. 배치가 cross-service로 수집한 값을 전달한다.
 * dailyProfit(전일 대비)·cumulativeReturnRate(seedCapital 대비)는 서비스가 계산한다.
 */
public record RecordDailySnapshotCommand(
        String userId,
        LocalDate snapshotDate,
        long totalAsset,
        long stockValue,
        long seedCapital,      // 누적 수익률 기준 원금
        String idempotencyKey
) {}
