package org.profit.candle.ranking.ranking.event;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.support.idempotency.RankingCommandRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RankingOutboxPublisher {

    private static final String TOPIC = "ranking.daily-finalized.v1";
    private static final int PUBLISH_BATCH_SIZE = 100;

    private final RankingCommandRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    /** 미발행 완료 이벤트를 Kafka로 전송하고 성공한 행만 발행 완료 처리한다. */
    @Scheduled(fixedDelay = 5_000L)
    @Transactional
    public void publishPendingEvents() {
        repository.findPendingOutbox(PUBLISH_BATCH_SIZE).forEach(event -> {
            kafkaTemplate.send(TOPIC, event.aggregateId(), event.payload()).join();
            repository.markOutboxPublished(event.eventId(), clock.instant());
        });
    }
}
