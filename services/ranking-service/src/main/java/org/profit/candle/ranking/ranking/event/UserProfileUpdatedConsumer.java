package org.profit.candle.ranking.ranking.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.ranking.ranking.service.RankingParticipantProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileUpdatedConsumer {

    static final String TOPIC = "user.profile-updated.v1";

    private final ObjectMapper objectMapper;
    private final RankingParticipantProjectionService projectionService;

    /** User 프로필 변경 이벤트를 검증한 뒤 참가자 투영 서비스로 전달한다. */
    @KafkaListener(topics = TOPIC)
    public void onUserProfileUpdated(String rawPayload) {
        UserProfileUpdatedEvent event;
        try {
            event = objectMapper.readValue(rawPayload, UserProfileUpdatedEvent.class);
        } catch (Exception exception) {
            log.warn("UserProfileUpdated event could not be deserialized");
            return;
        }

        if (!valid(event)) {
            log.warn("UserProfileUpdated event is invalid");
            return;
        }
        projectionService.projectProfile(event);
    }

    /** 이벤트 버전과 필수 필드가 현재 Ranking 계약에 맞는지 검사한다. */
    private boolean valid(UserProfileUpdatedEvent event) {
        if (event.eventId() == null || event.occurredAt() == null
                || !"UserProfileUpdated".equals(event.eventType()) || event.eventVersion() != 1
                || event.userId() == null || event.userId().isBlank()
                || event.nickname() == null || event.nickname().isBlank() || event.nickname().length() > 100) {
            return false;
        }
        try {
            java.util.UUID.fromString(event.userId());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
