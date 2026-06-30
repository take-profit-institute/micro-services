package org.profit.candle.chatting.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채팅 게이트웨이 설정. {@code chat.*} 프리픽스를 한곳에 묶는다(생성자 바인딩).
 *
 * @param jwt  핸드셰이크 검증용 HMAC 설정
 * @param room 방 정책(정원, 카운터 TTL)
 */
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(Jwt jwt, Room room) {

    public record Jwt(String hmacSecret) {
    }

    public record Room(int capacity, Duration counterTtl) {
    }
}
