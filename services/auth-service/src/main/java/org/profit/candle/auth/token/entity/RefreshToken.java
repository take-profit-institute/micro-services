package org.profit.candle.auth.token.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrincipalType principalType;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column
    private Instant revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID id, UUID userId, String tokenHash, PrincipalType principalType, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.principalType = principalType;
        this.expiresAt = expiresAt;
    }

    public UUID userId() { return userId; }
    public PrincipalType principalType() { return principalType; }
    public boolean usableAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke(Instant now) { this.revokedAt = now; }
}
