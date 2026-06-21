package org.profit.candle.auth.identity.repository;

import java.util.Optional;
import org.profit.candle.auth.identity.entity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, java.util.UUID> {
    Optional<OAuthAccount> findByProviderAndProviderSubject(String provider, String providerSubject);
}
