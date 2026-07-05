package org.profit.candle.market.ranking.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.constant.StockRankingRedisKey;
import org.profit.candle.market.ranking.dto.cache.RankingSnapshot;
import org.profit.candle.proto.market.v1.RankingType;
import org.springframework.stereotype.Service;

/**
 * 트렌딩 랭킹 읽기 전용 경로. proto RankingType 을 Redis 캐시 키로 매핑해 스냅샷을 읽는다.
 * 키움 API 는 절대 타지 않는다(쓰기는 StockRankingScheduler 소관). 캐시 miss 는 null 로 반환하고
 * gRPC 계층이 UNAVAILABLE 로 변환한다.
 */
@Service
@RequiredArgsConstructor
public class RankingReadService {

    private final RankingCacheService rankingCacheService;

    public RankingSnapshot read(RankingType type) {
        return rankingCacheService.read(redisKeyOf(type));
    }

    private String redisKeyOf(RankingType type) {
        return switch (type) {
            case RISING -> StockRankingRedisKey.RISING;
            case FALLING -> StockRankingRedisKey.FALLING;
            case VOLUME_SPIKE -> StockRankingRedisKey.VOLUME_SPIKE;
            case POPULAR -> StockRankingRedisKey.POPULAR;
            case RATE_UP -> StockRankingRedisKey.RATE_UP;
            case RATE_DOWN -> StockRankingRedisKey.RATE_DOWN;
            case RANKING_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("ranking type must be specified");
        };
    }
}
