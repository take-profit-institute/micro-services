package org.profit.candle.learning.content.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contents", schema = "learning")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String category;

    @Convert(converter = ContentLevelConverter.class)
    @Column(name = "level", nullable = false, columnDefinition = "learning.content_level_type")
    private ContentLevel level;

    private String body;

    @Column(name = "duration_min", nullable = false)
    private short durationMin;

    @Column(name = "xp_reward", nullable = false)
    private long xpReward;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "keywords", columnDefinition = "text[]")
    private String[] keywords = {};

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "read_count", nullable = false)
    private long readCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // === 생성 ===
    public static Content create(String title, String description, String category,
                                 ContentLevel level, String body, short durationMin,
                                 long xpReward, String[] keywords, boolean published) {
        Content c = new Content();
        c.title = title;
        c.description = description;
        c.category = category;
        c.level = level;
        c.body = body;
        c.durationMin = durationMin;
        c.xpReward = xpReward;
        c.keywords = keywords;
        c.published = published;
        c.readCount = 0;
        return c;
    }

    // === 수정 ===
    public void update(String title, String description, String category,
                       ContentLevel level, String body, Short durationMin,
                       Long xpReward, String[] keywords, Boolean published) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (level != null) this.level = level;
        if (body != null) this.body = body;
        if (durationMin != null) this.durationMin = durationMin;
        if (xpReward != null) this.xpReward = xpReward;
        if (keywords != null) this.keywords = keywords;
        if (published != null) this.published = published;
    }

    // === Soft Delete ===
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    // === 조회수 증가: Repository에서 atomic query로 처리 ===
}