package org.profit.candle.batch.ranking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 일별 랭킹 확정 Job의 실행과 gRPC 설정이다. */
@ConfigurationProperties(prefix = "batch.ranking")
public record RankingBatchProperties(
        String grpcTarget,
        long deadlineMillis
) {
}
