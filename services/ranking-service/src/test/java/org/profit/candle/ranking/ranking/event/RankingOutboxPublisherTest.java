package org.profit.candle.ranking.ranking.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.ranking.ranking.cache.RankingCache;
import org.profit.candle.ranking.support.idempotency.RankingCommandRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;

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

    /** Redis 장애가 발생해도 Kafka 발행 완료 처리를 유지하는지 검증한다. */
    @Test
    void publishPendingEventsKeepsPublishedStateWhenRedisIsUnavailable() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-03T06:30:00Z");
        var event = new RankingCommandRepository.OutboxEvent(
                eventId, "DailyRankingFinalized", "2026-07-03", "{}", now);
        when(repository.findPendingOutbox(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send("ranking.daily-finalized.v1", "2026-07-03", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(rankingCache).putLatestDate(java.time.LocalDate.of(2026, 7, 3));
        RankingOutboxPublisher publisher = new RankingOutboxPublisher(
                repository, kafkaTemplate, Clock.fixed(now, ZoneOffset.UTC), rankingCache);

        publisher.publishPendingEvents();

        verify(repository).markOutboxPublished(eventId, now);
    }

    /** 잘못된 Outbox 날짜는 Redis 장애로 숨기지 않고 재처리할 수 있도록 실패시키는지 검증한다. */
    @Test
    void publishPendingEventsRejectsInvalidRankingDate() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-03T06:30:00Z");
        var event = new RankingCommandRepository.OutboxEvent(
                eventId, "DailyRankingFinalized", "invalid-date", "{}", now);
        when(repository.findPendingOutbox(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send("ranking.daily-finalized.v1", "invalid-date", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));
        RankingOutboxPublisher publisher = new RankingOutboxPublisher(
                repository, kafkaTemplate, Clock.fixed(now, ZoneOffset.UTC), rankingCache);

        assertThatThrownBy(publisher::publishPendingEvents)
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }
}
