package org.profit.candle.ranking.ranking.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.ranking.entity.ConsumedEvent;
import org.profit.candle.ranking.ranking.entity.ConsumedEventId;
import org.profit.candle.ranking.ranking.entity.RankingParticipant;
import org.profit.candle.ranking.ranking.event.UserProfileUpdatedEvent;
import org.profit.candle.ranking.ranking.repository.ConsumedEventRepository;
import org.profit.candle.ranking.ranking.repository.RankingParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultRankingParticipantProjectionService implements RankingParticipantProjectionService {

    static final String SOURCE_SERVICE = "user-service";

    private final RankingParticipantRepository participantRepository;
    private final ConsumedEventRepository consumedEventRepository;

    /** 이벤트를 한 번만 처리하며 참가자와 소비 이력을 같은 트랜잭션에 저장한다. */
    @Override
    @Transactional
    public void projectProfile(UserProfileUpdatedEvent event) {
        ConsumedEventId consumedEventId = new ConsumedEventId(SOURCE_SERVICE, event.eventId());
        if (consumedEventRepository.existsById(consumedEventId)) {
            return;
        }

        UUID userId = UUID.fromString(event.userId());
        RankingParticipant participant = participantRepository.findById(userId)
                .orElseGet(() -> RankingParticipant.fromProfile(userId, event.nickname(), event.occurredAt()));
        participant.updateNickname(event.nickname(), event.occurredAt());
        participantRepository.save(participant);
        consumedEventRepository.save(new ConsumedEvent(
                SOURCE_SERVICE, event.eventId(), event.eventType()));
    }
}
