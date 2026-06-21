package org.profit.candle.trading.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.event.entity.OutboxEvent;
import org.profit.candle.trading.event.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

/** 도메인 이벤트를 outbox에 기록한다 (호출 트랜잭션 내에서 commit). */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void record(String eventType, String aggregateId, Object payload) {
        Instant now = Instant.now();
        try {
            outboxEventRepository.save(new OutboxEvent(
                    UUID.randomUUID(),
                    eventType,
                    aggregateId,
                    objectMapper.writeValueAsString(payload),
                    now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(eventType + " event serialization failed", exception);
        }
    }
}
