package org.profit.candle.user.profile.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.event.entity.OutboxEvent;
import org.profit.candle.user.profile.event.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void writeUserProfileUpdated(UserProfileResult result) {
        UserProfileUpdatedEvent event = UserProfileUpdatedEvent.of(
                result.userId(), result.nickname(), result.profileImageUrl());
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new OutboxEvent(UserProfileEvents.TOPIC, result.userId(), payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("UserProfileUpdated 이벤트 직렬화 실패", e);
        }
    }
}
