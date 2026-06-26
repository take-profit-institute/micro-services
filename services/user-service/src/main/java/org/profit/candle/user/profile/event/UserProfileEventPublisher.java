package org.profit.candle.user.profile.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserProfileEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishUserProfileUpdated(UserProfileResult result) {
        UserProfileUpdatedEvent event = UserProfileUpdatedEvent.of(
                result.userId(), result.nickname(), result.profileImageUrl());
        try {
            kafkaTemplate.send(UserProfileUpdatedEvent.TOPIC, result.userId(),
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("UserProfileUpdated 이벤트 직렬화 실패", e);
        }
    }
}
