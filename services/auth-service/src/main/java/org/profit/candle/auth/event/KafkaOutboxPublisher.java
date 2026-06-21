package org.profit.candle.auth.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.event.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class KafkaOutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${auth.outbox.publish-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(event -> {
            kafkaTemplate.send(topicFor(event.eventType()), event.aggregateId(), event.payload()).join();
            event.markPublished(Instant.now());
        });
    }

    private String topicFor(String eventType) {
        if (AuthEventType.USER_CREATED.wireName().equals(eventType)) {
            return AuthEventType.USER_CREATED.topic();
        }
        throw new IllegalStateException("Unsupported auth event type: " + eventType);
    }
}
