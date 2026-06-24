package org.profit.candle.trading.support.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 도메인 이벤트를 outbox에 기록한다 (호출 트랜잭션 내에서 commit).
 *
 * 도메인마다 outbox_events 테이블이 별도 스키마에 존재하므로(SQL 컨벤션 9장),
 * 이 클래스는 어떤 Repository를 쓸지 알지 못한다. 호출하는 쪽이 자기 도메인의
 * Repository를 {@link OutboxOperations}로 넘겨준다.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final ObjectMapper objectMapper;

    public <REC> void record(OutboxOperations<REC> ops, String eventType, String aggregateId, Object payload) {
        Instant now = Instant.now();
        try {
            REC event = ops.newEvent(
                    UUID.randomUUID(),
                    eventType,
                    aggregateId,
                    objectMapper.writeValueAsString(payload),
                    now);
            ops.save(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(eventType + " event serialization failed", exception);
        }
    }
}