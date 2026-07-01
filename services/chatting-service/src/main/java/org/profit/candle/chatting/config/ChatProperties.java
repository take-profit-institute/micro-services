package org.profit.candle.chatting.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채팅 게이트웨이 설정. {@code chat.*} 프리픽스를 한곳에 묶는다(생성자 바인딩).
 *
 * @param jwt  핸드셰이크 검증용 HMAC 설정
 * @param room 방 정책(정원, 카운터 TTL)
 * @param cors 방 배정 REST용 CORS(웹앱이 dev에서 chatting-service에 직결하므로 필요)
 */
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(Jwt jwt, Room room, Cors cors) {

    /**
     * @param hmacSecret auth-service 서명 시크릿과 동일(HS256)
     * @param issuer     기대 issuer(auth-service의 {@code AUTH_JWT_ISSUER}와 동일). 비우면 issuer 검증 생략.
     */
    public record Jwt(String hmacSecret, String issuer) {
    }

    public record Room(int capacity, Duration counterTtl) {
    }

    public record Cors(List<String> allowedOriginPatterns) {
    }
}
