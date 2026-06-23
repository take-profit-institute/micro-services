package org.profit.candle.user.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    @Column(length = 36)
    private String userId;

    @Column(nullable = false, length = 320, unique = true)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserProfileEntity() {}

    public UserProfileEntity(String userId, String email, String nickname, String profileImageUrl) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.deleted = false;
        this.updatedAt = Instant.now();
    }

    public String userId() { return userId; }
    public String email() { return email; }
    public String nickname() { return nickname; }
    public String profileImageUrl() { return profileImageUrl; }
    public boolean deleted() { return deleted; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.updatedAt = Instant.now();
    }

    public void delete() {
        this.deleted = true;
        this.updatedAt = Instant.now();
    }
}
