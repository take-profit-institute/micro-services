package org.profit.candle.market.ranking.dto.cache;

import java.time.Instant;
import java.util.List;

/**
 * Redis 에 저장되는 랭킹 캐시 1건. 항목 리스트에 더해 기준 시각(asOf)을 함께 실어
 * 읽기 경로(GetRankings)가 신선도("15:32 기준")를 내려줄 수 있게 한다.
 * GenericJacksonJsonRedisSerializer 가 @class 타입정보와 함께 직렬화하므로 그대로 라운드트립된다.
 */
public record RankingSnapshot(
        List<StockRankingCacheItem> items,
        Instant asOf
) {
}
