package org.profit.candle.wishlist.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.wishlist.event.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 의 미발행 이벤트를 Kafka 로 발행한다. key=symbol 로 심볼별 partition 순서를 유지한다.
 * 전송 성공 시에만 published_at 을 기록 — 실패하면 다음 실행이 재시도한다(at-least-once).
 */
@Component
@RequiredArgsConstructor
public class KafkaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${wishlist.outbox.publish-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(event -> {
            kafkaTemplate.send(WishlistEventType.TOPIC, event.aggregateId(), event.payload()).join();
            event.markPublished(Instant.now());
        });
    }
}
