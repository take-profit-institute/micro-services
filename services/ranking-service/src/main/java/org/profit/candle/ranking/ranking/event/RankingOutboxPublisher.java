package org.profit.candle.ranking.ranking.event;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.profit.candle.ranking.ranking.cache.RankingCache;
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
    private final RankingCache rankingCache;

    /** 미발행 완료 이벤트를 Kafka로 전송하고 성공한 행만 발행 완료 처리한다. */
    @Scheduled(fixedDelay = 5_000L)
    @Transactional
    public void publishPendingEvents() {
        repository.findPendingOutbox(PUBLISH_BATCH_SIZE).forEach(event -> {
            kafkaTemplate.send(TOPIC, event.aggregateId(), event.payload()).join();
            repository.markOutboxPublished(event.eventId(), clock.instant());
            refreshLatestDate(event.aggregateId());
        });
    }

    /** 새 랭킹 완료 후 latest-date 캐시를 갱신하되 Redis 장애는 Outbox 발행을 되돌리지 않는다. */
    private void refreshLatestDate(String rankingDate) {
        try {
            rankingCache.putLatestDate(LocalDate.parse(rankingDate));
        } catch (RuntimeException ignored) {
            // 다음 조회가 DB fallback으로 캐시를 다시 생성한다.
        }
    }
}
