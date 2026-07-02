package org.profit.candle.stock.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.event.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 미발행 outbox 행을 주기적으로 Kafka 로 발행한다. 실패 시 published_at 을 갱신하지 않아 다음 주기에 재시도한다. */
@Component
@RequiredArgsConstructor
public class KafkaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${stock.outbox.publish-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(event -> {
            kafkaTemplate.send(topicFor(event.eventType()), event.aggregateId(), event.payload()).join();
            event.markPublished(Instant.now());
        });
    }

    private String topicFor(String eventType) {
        if (StockEventType.STOCK_DAILY_CLOSED.wireName().equals(eventType)) {
            return StockEventType.STOCK_DAILY_CLOSED.topic();
        }
        throw new IllegalStateException("Unsupported stock event type: " + eventType);
    }
}
