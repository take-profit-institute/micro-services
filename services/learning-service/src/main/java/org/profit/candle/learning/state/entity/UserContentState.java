package org.profit.candle.learning.state.entity;

import jakarta.persistence.*;
import lombok.*;
import org.profit.candle.learning.content.entity.Content;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_content_states", schema = "learning")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserContentState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(name = "progress_pct", nullable = false)
    private short progressPct;

    @Getter(AccessLevel.NONE)
    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    @Getter(AccessLevel.NONE)
    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean completed() { return completed; }
    public boolean favorite() { return favorite; }

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

    public static UserContentState create(UUID userId, Content content) {
        UserContentState s = new UserContentState();
        s.userId = userId;
        s.content = content;
        s.progressPct = 0;
        s.completed = false;
        s.favorite = false;
        return s;
    }

    public void updateProgress(short progressPct) {
        if (progressPct < 0 || progressPct > 100) {
            throw new IllegalArgumentException("progress_pct must be 0~100");
        }
        this.progressPct = progressPct;
        this.lastReadAt = Instant.now();
        if (progressPct == 100 && !this.completed) {
            markCompleted();
        }
    }

    public void markCompleted() {
        this.completed = true;
        this.progressPct = 100;
        this.completedAt = Instant.now();
        this.lastReadAt = Instant.now();
    }

    public void toggleFavorite() {
        this.favorite = !this.favorite;
    }
}