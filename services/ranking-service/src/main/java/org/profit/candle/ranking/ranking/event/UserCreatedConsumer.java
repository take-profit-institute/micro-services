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
public class UserCreatedConsumer {

    static final String TOPIC = "auth.user-created.v1";

    private final ObjectMapper objectMapper;
    private final RankingParticipantProjectionService projectionService;

    /** 사용자 생성 이벤트를 검증한 뒤 신규 참가자를 랭킹 명단에 등록한다. */
    @KafkaListener(topics = TOPIC)
    public void onUserCreated(String rawPayload) {
        UserCreatedEvent event;
        try {
            event = objectMapper.readValue(rawPayload, UserCreatedEvent.class);
        } catch (Exception exception) {
            log.warn("UserCreated event could not be deserialized");
            return;
        }

        if (!valid(event)) {
            log.warn("UserCreated event is invalid");
            return;
        }
        projectionService.registerParticipant(event);
    }

    /** 이벤트 식별자·버전·필수 필드가 현재 Ranking 계약에 맞는지 검사한다. */
    private boolean valid(UserCreatedEvent event) {
        return event.eventId() != null
                && event.occurredAt() != null
                && event.userId() != null
                && "UserCreated".equals(event.eventType())
                && event.eventVersion() == 1;
    }
}
