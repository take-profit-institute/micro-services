package org.profit.candle.portfolio.holding.trade.repository;

import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaRealizedTradeRepository
        extends JpaRepository<RealizedTradeEntity, Long>, RealizedTradeReader, RealizedTradeWriter {

    @Override
    @Query("SELECT t FROM RealizedTradeEntity t WHERE t.userId = :userId ORDER BY t.closedAt")
    List<RealizedTradeEntity> findByUserId(@Param("userId") String userId);
}
