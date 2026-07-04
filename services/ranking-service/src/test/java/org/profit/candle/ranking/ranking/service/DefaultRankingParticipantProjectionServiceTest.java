package org.profit.candle.ranking.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.ranking.ranking.entity.ConsumedEvent;
import org.profit.candle.ranking.ranking.entity.ConsumedEventId;
import org.profit.candle.ranking.ranking.entity.ParticipantStatus;
import org.profit.candle.ranking.ranking.entity.RankingParticipant;
import org.profit.candle.ranking.ranking.event.UserProfileUpdatedEvent;
import org.profit.candle.ranking.ranking.repository.ConsumedEventRepository;
import org.profit.candle.ranking.ranking.repository.RankingParticipantRepository;

@ExtendWith(MockitoExtension.class)
class DefaultRankingParticipantProjectionServiceTest {

    @Mock
    RankingParticipantRepository participantRepository;

    @Mock
    ConsumedEventRepository consumedEventRepository;

    @InjectMocks
    DefaultRankingParticipantProjectionService service;

    /** 최초 프로필 이벤트가 비대상 상태의 참가자와 소비 이력을 생성하는지 검증한다. */
    @Test
    void projectProfileCreatesAnIneligibleParticipantAndRecordsTheEvent() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-03T06:30:00Z");
        UserProfileUpdatedEvent event = event(eventId, userId, "chanmi", occurredAt);
        when(consumedEventRepository.existsById(new ConsumedEventId("user-service", eventId))).thenReturn(false);
        when(participantRepository.findById(userId)).thenReturn(Optional.empty());

        service.projectProfile(event);

        ArgumentCaptor<RankingParticipant> participantCaptor = ArgumentCaptor.forClass(RankingParticipant.class);
        verify(participantRepository).save(participantCaptor.capture());
        RankingParticipant participant = participantCaptor.getValue();
        assertThat(participant.nickname()).isEqualTo("chanmi");
        assertThat(participant.tradeCount()).isZero();
        assertThat(participant.userStatus()).isEqualTo(ParticipantStatus.UNKNOWN);
        assertThat(participant.accountStatus()).isEqualTo(ParticipantStatus.UNKNOWN);
        verify(consumedEventRepository).save(org.mockito.ArgumentMatchers.any(ConsumedEvent.class));
    }

    /** 같은 이벤트를 다시 받았을 때 참가자 상태가 중복 변경되지 않는지 검증한다. */
    @Test
    void projectProfileIgnoresAnAlreadyConsumedEvent() {
        UUID eventId = UUID.randomUUID();
        ConsumedEventId consumedEventId = new ConsumedEventId("user-service", eventId);
        when(consumedEventRepository.existsById(consumedEventId)).thenReturn(true);

        service.projectProfile(event(eventId, UUID.randomUUID(), "chanmi", Instant.now()));

        verify(participantRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(participantRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(consumedEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /** 테스트 입력에 사용할 프로필 이벤트를 만든다. */
    private UserProfileUpdatedEvent event(UUID eventId, UUID userId, String nickname, Instant occurredAt) {
        return new UserProfileUpdatedEvent(
                eventId, "UserProfileUpdated", 1, userId.toString(), nickname, "", occurredAt);
    }
}
