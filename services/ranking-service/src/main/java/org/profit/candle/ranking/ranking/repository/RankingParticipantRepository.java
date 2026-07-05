package org.profit.candle.ranking.ranking.repository;

import java.util.UUID;
import org.profit.candle.ranking.ranking.entity.RankingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RankingParticipantRepository extends JpaRepository<RankingParticipant, UUID> {

    /** 동시 체결에서도 증가분이 유실되지 않도록 거래 횟수를 DB에서 원자적으로 증가시킨다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RankingParticipant participant "
            + "SET participant.tradeCount = participant.tradeCount + 1 "
            + "WHERE participant.userId = :userId")
    int incrementTradeCount(@Param("userId") UUID userId);
}
