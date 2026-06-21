package org.profit.candle.auth.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_oauth_accounts_provider_subject", columnNames = {"provider", "provider_subject"}))
public class OAuthAccount {
    @Id
    private UUID userId;
    @Column(nullable = false, length = 20)
    private String provider;
    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected OAuthAccount() {
    }

    public OAuthAccount(UUID userId, String provider, String providerSubject, String email, Instant now) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID userId() { return userId; }
    public String provider() { return provider; }
    public String providerSubject() { return providerSubject; }
    public String email() { return email; }
}
