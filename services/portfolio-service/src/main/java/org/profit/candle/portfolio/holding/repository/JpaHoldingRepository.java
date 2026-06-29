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
}
