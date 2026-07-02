package org.profit.candle.chatting.auth;

import java.util.Optional;

/**
 * WS 핸드셰이크 토큰을 검증하고 accountId를 확정한다.
 *
 * <p>chat-gateway가 자체 인증하므로 앞단(커스텀 Spring 프록시 / AWS ALB)에 무관하게 동작한다.
 */
public interface HandshakeAuthenticator {

    /** 토큰 검증 후 accountId 반환. 토큰이 없거나 유효하지 않으면 {@link Optional#empty()}. */
    Optional<String> authenticate(String token);
}
