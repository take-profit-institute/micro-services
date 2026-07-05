package org.profit.candle.ranking.ranking.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.ranking.entity.ConsumedEvent;
import org.profit.candle.ranking.ranking.entity.ConsumedEventId;
import org.profit.candle.ranking.ranking.entity.RankingParticipant;
import org.profit.candle.ranking.ranking.event.UserCreatedEvent;
import org.profit.candle.ranking.ranking.event.UserProfileUpdatedEvent;
import org.profit.candle.ranking.ranking.repository.ConsumedEventRepository;
import org.profit.candle.ranking.ranking.repository.RankingParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultRankingParticipantProjectionService implements RankingParticipantProjectionService {

    static final String SOURCE_SERVICE = "user-service";
    static final String AUTH_SOURCE_SERVICE = "auth-service";

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

    /** 이벤트를 한 번만 처리하며 신규 참가자와 소비 이력을 같은 트랜잭션에 저장한다. */
    @Override
    @Transactional
    public void registerParticipant(UserCreatedEvent event) {
        ConsumedEventId consumedEventId = new ConsumedEventId(AUTH_SOURCE_SERVICE, event.eventId());
        if (consumedEventRepository.existsById(consumedEventId)) {
            return;
        }

        UUID userId = event.userId();
        if (participantRepository.findById(userId).isEmpty()) {
            participantRepository.save(
                    RankingParticipant.forNewUser(userId, defaultNickname(event), event.occurredAt()));
        }
        consumedEventRepository.save(new ConsumedEvent(
                AUTH_SOURCE_SERVICE, event.eventId(), event.eventType()));
    }

    /** 생성 시점엔 닉네임이 없어 이메일 로컬 파트로 임시 표기한다(이후 프로필 이벤트가 갱신). */
    private static String defaultNickname(UserCreatedEvent event) {
        String email = event.email();
        if (email != null) {
            int at = email.indexOf('@');
            String local = (at > 0 ? email.substring(0, at) : email).trim();
            if (!local.isBlank()) {
                return local.length() > 100 ? local.substring(0, 100) : local;
            }
        }
        return "user-" + event.userId().toString().substring(0, 8);
    }
}
