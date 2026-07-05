package org.profit.candle.auth.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_admin_accounts_username", columnNames = "username"))
public class AdminAccount {
    @Id
    private UUID id;
    @Column(nullable = false, length = 50)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 100)
    private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminStatus status;
    @Column(nullable = false)
    private int failedAttempts;
    @Column
    private Instant lockedUntil;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Column
    private Instant lastLoginAt;

    protected AdminAccount() {
    }

    public AdminAccount(UUID id, String username, String passwordHash, String displayName,
            AdminRole role, AdminStatus status, Instant now) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.status = status;
        this.failedAttempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID id() { return id; }
    public String username() { return username; }
    public String passwordHash() { return passwordHash; }
    public String displayName() { return displayName; }
    public AdminRole role() { return role; }
    public AdminStatus status() { return status; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public Instant createdAt() { return createdAt; }

    public boolean isActive() { return status == AdminStatus.ACTIVE; }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordLoginSuccess(Instant now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public void recordLoginFailure(Instant now, int maxFailedAttempts, Duration lockDuration) {
        this.failedAttempts += 1;
        if (this.failedAttempts >= maxFailedAttempts) {
            this.lockedUntil = now.plus(lockDuration);
            this.failedAttempts = 0;
        }
        this.updatedAt = now;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void changeStatus(AdminStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }
}
