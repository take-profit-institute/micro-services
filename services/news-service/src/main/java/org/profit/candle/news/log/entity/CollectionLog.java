package org.profit.candle.news.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_logs", schema = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "collected_at", nullable = false, insertable = false, updatable = false)
    private Instant collectedAt;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "news.collection_status_type")
    private CollectionStatusType status;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    private CollectionLog(
            int targetCount,
            int successCount,
            int failCount,
            CollectionStatusType status,
            String message
    ) {
        this.targetCount = targetCount;
        this.successCount = successCount;
        this.failCount = failCount;
        this.status = status;
        this.message = message;
    }

    public static CollectionLog create(
            int targetCount,
            int successCount,
            int failCount,
            CollectionStatusType status,
            String message
    ) {
        return new CollectionLog(targetCount, successCount, failCount, status, message);
    }
}
