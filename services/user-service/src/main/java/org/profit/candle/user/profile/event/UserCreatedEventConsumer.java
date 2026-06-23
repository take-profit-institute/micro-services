package org.profit.candle.user.profile.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.user.profile.entity.UserProfileEntity;
import org.profit.candle.user.profile.event.dto.UserCreatedPayload;
import org.profit.candle.user.profile.event.entity.ConsumedEvent;
import org.profit.candle.user.profile.event.repository.ConsumedEventRepository;
import org.profit.candle.user.profile.repository.UserProfileWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventConsumer {

    private final ConsumedEventRepository consumedEventRepository;
    private final UserProfileWriter userProfileWriter;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.user-created.v1")
    @Transactional
    public void onUserCreated(String rawPayload) {
        UserCreatedPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, UserCreatedPayload.class);
        } catch (Exception e) {
            log.error("UserCreated 이벤트 역직렬화 실패. payload={}", rawPayload, e);
            return;
        }

        if (consumedEventRepository.existsById(payload.eventId())) {
            log.info("이미 처리된 이벤트 skip. eventId={}", payload.eventId());
            return;
        }

        try {
            userProfileWriter.save(new UserProfileEntity(
                    payload.userId().toString(),
                    payload.email(),
                    null,
                    null));
            consumedEventRepository.save(new ConsumedEvent(payload.eventId(), payload.eventType()));
            log.info("사용자 프로필 생성 완료. userId={}, email={}", payload.userId(), payload.email());
        } catch (DataIntegrityViolationException e) {
            // 프로필이 이미 존재하는 경우 (재시도 등) consumed_events만 기록하고 정상 처리
            log.warn("사용자 프로필 이미 존재. userId={}, eventId={}", payload.userId(), payload.eventId());
            consumedEventRepository.save(new ConsumedEvent(payload.eventId(), payload.eventType()));
        }
    }
}
