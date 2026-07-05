package org.profit.candle.portfolio.holding.repository;

import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.entity.HoldingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JpaHoldingRepository
        extends JpaRepository<HoldingEntity, HoldingId>, HoldingReader, HoldingWriter {

    @Override
    @Query("SELECT h FROM HoldingEntity h WHERE h.id.userId = :userId AND h.id.symbol = :symbol")
    Optional<HoldingEntity> findByUserIdAndSymbol(@Param("userId") String userId, @Param("symbol") String symbol);

    @Override
    @Query("SELECT h FROM HoldingEntity h WHERE h.id.userId = :userId ORDER BY h.id.symbol")
    List<HoldingEntity> findByUserId(@Param("userId") String userId);

    @Override
    @Query("SELECT h FROM HoldingEntity h WHERE h.id.userId = :userId AND h.active = true ORDER BY h.id.symbol")
    List<HoldingEntity> findActiveByUserId(@Param("userId") String userId);

    // keyset(user_id > lastUserId)로 활성 유저 id 를 user_id ASC 페이징. limit = pageSize+1 로 hasNext 판정.
    @Override
    @Query(value = """
            SELECT DISTINCT user_id
            FROM portfolio_holdings
            WHERE active = true AND quantity > 0
              AND (:lastUserId IS NULL OR user_id > :lastUserId)
            ORDER BY user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findActiveUserIdsAfter(
            @Param("lastUserId") String lastUserId,
            @Param("limit") int limit);

    @Override
    @Query("""
            SELECT h FROM HoldingEntity h
            WHERE h.active = true AND h.quantity > 0 AND h.id.userId IN :userIds
            ORDER BY h.id.userId ASC, h.id.symbol ASC
            """)
    List<HoldingEntity> findActiveHoldingsByUserIds(@Param("userIds") List<String> userIds);
}
