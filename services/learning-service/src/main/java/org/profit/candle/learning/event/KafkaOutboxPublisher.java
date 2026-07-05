package org.profit.candle.learning.event;

import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.event.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class KafkaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${learning.outbox.publish-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()
                .forEach(event -> {
                    kafkaTemplate.send(topicFor(event.eventType()), event.aggregateId(), event.payload()).join();
                    event.markPublished(Instant.now());
                });
    }

    private String topicFor(String eventType) {
        if (LearningEventType.LEARNING_COMPLETED.wireName().equals(eventType)) {
            return LearningEventType.LEARNING_COMPLETED.topic();
        }
        throw new IllegalStateException("Unsupported learning event type: " + eventType);
    }
}