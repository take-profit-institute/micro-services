package org.profit.candle.chatting;

import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 종목별 실시간 채팅 WS 게이트웨이.
 *
 * <p>설계 전제(docs/chat-architecture):
 * <ul>
 *   <li>메시지를 영속 저장하지 않음 — 실시간 라우팅에만 집중</li>
 *   <li>WS 서버는 Stateless — 커넥션/방 상태는 전부 Redis에 위임</li>
 *   <li>스케일아웃은 Redis Pub/Sub 팬아웃으로 해결 → sticky session 불필요</li>
 *   <li>핸드셰이크 인증을 chat-gateway가 자체 수행 → ALB/NLB passthrough 호환</li>
 * </ul>
 *
 * <p><b>컨벤션 예외(CONVENTIONS §6):</b> 본 서비스는 auth-service와 동일하게 게이트웨이가
 * HTTP/WS로 직접 라우팅하는 클라이언트 대면 게이트웨이다. 서비스 간 gRPC 통신이 아니므로
 * REST/WS 진입점을 둔다(인터-서비스 통신이 필요해지면 gRPC를 추가한다).
 */
@SpringBootApplication
@EnableConfigurationProperties(ChatProperties.class)
public class ChattingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChattingServiceApplication.class, args);
    }
}
