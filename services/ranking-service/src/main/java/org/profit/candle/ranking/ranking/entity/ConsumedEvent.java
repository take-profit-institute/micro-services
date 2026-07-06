package org.profit.candle.ranking.ranking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(ConsumedEventId.class)
@Table(name = "ranking_consumed_events")
public class ConsumedEvent {

    @Id
    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService;

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "consumed_at", nullable = false, insertable = false, updatable = false)
    private Instant consumedAt;

    /** JPA가 엔티티를 조회할 때 사용한다. */
    protected ConsumedEvent() {}

    /** 처리한 외부 이벤트의 중복 방지 정보를 생성한다. */
    public ConsumedEvent(String sourceService, UUID eventId, String eventType) {
        this.sourceService = sourceService;
        this.eventId = eventId;
        this.eventType = eventType;
    }
}
