package org.profit.candle.ranking.ranking.dto;

import java.util.UUID;
import org.profit.candle.ranking.ranking.entity.ParticipantStatus;

public record RankingParticipantCandidate(
        UUID userId,
        int tradeCount,
        ParticipantStatus userStatus,
        ParticipantStatus accountStatus) {

    /** 거래 횟수와 사용자·계좌 상태가 랭킹 대상 조건을 만족하는지 반환한다. */
    public boolean eligible() {
        return tradeCount >= 5
                && userStatus == ParticipantStatus.ACTIVE
                && accountStatus == ParticipantStatus.ACTIVE;
    }
}
