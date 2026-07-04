package org.profit.candle.ranking.ranking.repository;

import java.util.UUID;
import org.profit.candle.ranking.ranking.entity.RankingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingParticipantRepository extends JpaRepository<RankingParticipant, UUID> {}
