package org.profit.candle.stock.event.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.profit.candle.stock.event.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();

    /**
     * 같은 id 의 행이 이미 있으면 아무것도 하지 않고 0 을 돌려준다(= 이미 기록된 이벤트).
     *
     * <p>{@code save()} 를 쓰지 않는다: id 가 assigned 라 Spring Data 가 merge 로 처리해
     * 행마다 SELECT 가 선행되고, 기존 행이 있으면 {@code published_at} 까지 null 로 덮어써
     * 이미 발행된 이벤트를 다시 발행하게 된다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO outbox_events (id, event_type, aggregate_id, payload, occurred_at)
            VALUES (:id, :eventType, :aggregateId, :payload, :occurredAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("eventType") String eventType,
                       @Param("aggregateId") String aggregateId,
                       @Param("payload") String payload,
                       @Param("occurredAt") Instant occurredAt);
}
