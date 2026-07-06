package org.profit.candle.ranking.ranking.event;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.ranking.ranking.service.RankingParticipantProjectionService;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UserProfileUpdatedConsumerTest {

    @Mock
    ObjectMapper objectMapper;

    @Mock
    RankingParticipantProjectionService projectionService;

    @InjectMocks
    UserProfileUpdatedConsumer consumer;

    /** 정상 이벤트가 투영 서비스로 전달되는지 검증한다. */
    @Test
    void onUserProfileUpdatedProjectsAValidEvent() throws Exception {
        UserProfileUpdatedEvent event = validEvent();
        when(objectMapper.readValue("{}", UserProfileUpdatedEvent.class)).thenReturn(event);

        consumer.onUserProfileUpdated("{}");

        verify(projectionService).projectProfile(event);
    }

    /** 사용자 ID 형식이 잘못된 이벤트가 저장 단계로 전달되지 않는지 검증한다. */
    @Test
    void onUserProfileUpdatedIgnoresAnInvalidEvent() throws Exception {
        UserProfileUpdatedEvent event = new UserProfileUpdatedEvent(
                UUID.randomUUID(), "UserProfileUpdated", 1, "invalid", "chanmi", "", Instant.now());
        when(objectMapper.readValue("{}", UserProfileUpdatedEvent.class)).thenReturn(event);

        consumer.onUserProfileUpdated("{}");

        verify(projectionService, never()).projectProfile(org.mockito.ArgumentMatchers.any());
    }

    /** DB 처리 실패가 숨겨지지 않아 Kafka 재시도가 가능한지 검증한다. */
    @Test
    void onUserProfileUpdatedDoesNotHideProjectionFailures() throws Exception {
        UserProfileUpdatedEvent event = validEvent();
        when(objectMapper.readValue("{}", UserProfileUpdatedEvent.class)).thenReturn(event);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(projectionService).projectProfile(event);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.onUserProfileUpdated("{}"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 테스트에서 공통으로 사용할 정상 프로필 이벤트를 만든다. */
    private UserProfileUpdatedEvent validEvent() {
        return new UserProfileUpdatedEvent(
                UUID.randomUUID(), "UserProfileUpdated", 1, UUID.randomUUID().toString(),
                "chanmi", "", Instant.parse("2026-07-03T06:30:00Z"));
    }
}
