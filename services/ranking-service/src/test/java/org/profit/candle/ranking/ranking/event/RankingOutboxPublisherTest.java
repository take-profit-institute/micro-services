package org.profit.candle.ranking.ranking.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.ranking.ranking.cache.RankingCache;
import org.profit.candle.ranking.support.idempotency.RankingCommandRepository;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class RankingOutboxPublisherTest {

    @Mock
    RankingCommandRepository repository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    RankingCache rankingCache;

    /** Kafka 전송 성공 후에만 Outbox 발행 완료 시간이 기록되는지 검증한다. */
    @Test
    void publishPendingEventsMarksSuccessfulEventsAsPublished() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-03T06:30:00Z");
        var event = new RankingCommandRepository.OutboxEvent(
                eventId, "DailyRankingFinalized", "2026-07-03", "{}", now);
        when(repository.findPendingOutbox(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send("ranking.daily-finalized.v1", "2026-07-03", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));
        RankingOutboxPublisher publisher = new RankingOutboxPublisher(
                repository, kafkaTemplate, Clock.fixed(now, ZoneOffset.UTC), rankingCache);

        publisher.publishPendingEvents();

        verify(repository).markOutboxPublished(eventId, now);
        verify(rankingCache).putLatestDate(java.time.LocalDate.of(2026, 7, 3));
    }
}
