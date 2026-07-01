package org.profit.candle.auth.identity.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.repository.OAuthAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultAuthMeService implements AuthMeService {

    private final OAuthAccountRepository oAuthAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthUserResult getMe(String userId) {
        UUID id = parseUserId(userId);
        return oAuthAccountRepository.findById(id)
                .map(AuthUserResult::from)
                .orElseThrow(() -> new AuthException(AuthErrorCode.AUTH_USER_NOT_FOUND));
    }

    private UUID parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_USER_ID);
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(AuthErrorCode.INVALID_AUTH_USER_ID, exception);
        }
    }
}
