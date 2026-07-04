package org.profit.candle.ranking.ranking.service;

import org.profit.candle.ranking.ranking.event.UserProfileUpdatedEvent;

public interface RankingParticipantProjectionService {

    /** 프로필 변경을 Ranking 전용 참가자 데이터에 반영한다. */
    void projectProfile(UserProfileUpdatedEvent event);
}
