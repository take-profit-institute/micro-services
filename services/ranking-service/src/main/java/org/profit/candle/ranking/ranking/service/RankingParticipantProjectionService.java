package org.profit.candle.ranking.ranking.service;

import org.profit.candle.ranking.ranking.event.OrderFilledEvent;
import org.profit.candle.ranking.ranking.event.UserCreatedEvent;
import org.profit.candle.ranking.ranking.event.UserProfileUpdatedEvent;

public interface RankingParticipantProjectionService {

    /** 체결 이벤트를 한 번만 반영해 참가자의 누적 거래 횟수를 증가시킨다. */
    void projectFilledOrder(OrderFilledEvent event);

    /** 프로필 변경을 Ranking 전용 참가자 데이터에 반영한다. */
    void projectProfile(UserProfileUpdatedEvent event);

    /** 사용자 생성 이벤트를 받아 신규 참가자를 랭킹 명단에 등록한다(꼴찌 진입). */
    void registerParticipant(UserCreatedEvent event);
}
