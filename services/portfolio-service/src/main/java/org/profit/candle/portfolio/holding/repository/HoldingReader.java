package org.profit.candle.portfolio.holding.repository;

import org.profit.candle.portfolio.holding.entity.HoldingEntity;

import java.util.List;
import java.util.Optional;

public interface HoldingReader {
    Optional<HoldingEntity> findByUserIdAndSymbol(String userId, String symbol);
    List<HoldingEntity> findByUserId(String userId);
    List<HoldingEntity> findActiveByUserId(String userId);

    // 배치용 활성 보유자 순회. user_id ASC keyset — active=true(=quantity>0)인 유저만.
    List<String> findActiveUserIdsAfter(String lastUserId, int limit);
    // 주어진 유저 집합의 활성 보유종목을 (user_id, symbol) ASC 로 반환.
    List<HoldingEntity> findActiveHoldingsByUserIds(List<String> userIds);
}
