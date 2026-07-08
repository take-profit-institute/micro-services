package org.profit.candle.chatting.ws;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.chatting.auth.HandshakeAuthenticator;
import org.profit.candle.chatting.config.ChatProperties;
import org.profit.candle.chatting.room.RoomCounter;
import org.profit.candle.chatting.room.RoomKey;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 채팅 WS 커넥션 핸들러.
 *
 * <p>흐름: 핸드셰이크 토큰 검증 → presence 등록(enter) → Redis 채널 구독(outbound) +
 * 수신 메시지 PUBLISH(inbound) + 주기적 heartbeat(presence 갱신). 커넥션 종료 시 leave로 정리한다.
 * heartbeat가 끊긴(비정상 종료) 멤버는 presenceTtl 경과 후 카운트에서 자동 제외되어 인원이 새지 않는다.
 *
 * <p>토큰 전달: 1순위 쿼리스트링 {@code ?token=}, 2순위 {@code Sec-WebSocket-Protocol} 헤더.
 * 브라우저 {@code WebSocket} API는 커스텀 헤더를 못 붙이므로 쿼리/서브프로토콜을 쓴다.
 * 쿼리 토큰은 로그에 남을 수 있으니 짧은 수명 토큰을 권장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    /** 비표준 close code: 인증 실패(4000~4999는 애플리케이션 정의 가능 범위). */
    private static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");

    private final HandshakeAuthenticator authenticator;
    private final RoomCounter roomCounter;
    private final ChatBroker broker;
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Map<String, String> query = parseQuery(session.getHandshakeInfo().getUri().getRawQuery());

        String roomId = query.get("room");
        if (roomId == null || roomId.isBlank()) {
            return session.close(CloseStatus.BAD_DATA.withReason("room required"));
        }
        RoomKey key;
        try {
            key = RoomKey.parse(roomId);
        } catch (RuntimeException e) {
            return session.close(CloseStatus.BAD_DATA.withReason("invalid room"));
        }

        String token = Optional.ofNullable(query.get("token")).orElseGet(() -> subProtocolToken(session));
        Optional<String> account = authenticator.authenticate(token);
        if (account.isEmpty()) {
            return session.close(UNAUTHORIZED);
        }
        String accountId = account.get();
        String channel = key.channel();
        // presence member = 커넥션 단위 고유 id. 재연결은 새 member라 이전 커넥션은 heartbeat 중단으로
        // 만료되고(중복 카운트가 presenceTtl 내로 한정), 크래시도 같은 방식으로 자가치유된다.
        String memberId = accountId + ":" + UUID.randomUUID();

        // heartbeat: 커넥션이 살아있는 동안 주기적으로 presence score를 갱신한다. 메시지를 방출하지 않고
        // outbound에 합류시켜, 연결이 끝나면(send 취소) heartbeat도 함께 취소되도록 수명을 묶는다.
        Flux<WebSocketMessage> heartbeat = Flux.interval(properties.room().heartbeat())
                .concatMap(tick -> roomCounter.heartbeat(key, memberId)
                        .onErrorResume(e -> {
                            log.warn("heartbeat 실패 room={} member={}: {}", roomId, memberId, e.getMessage());
                            return Mono.empty();
                        })
                        .then(Mono.empty()));

        Flux<WebSocketMessage> outbound = Flux.merge(
                broker.subscribe(channel).map(session::textMessage),
                heartbeat);

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(text -> Mono.fromCallable(() -> serialize(accountId, text))
                        .flatMap(payload -> broker.publish(channel, payload)))
                .then();

        // enter(presence 등록)로 입장 → 어느 한쪽(수신 종료=클라 끊김, 또는 송신 종료)이 끝나면 정리.
        // usingWhen: enter(자원 획득)가 성공했을 때만 leave(자원 해제)를 호출한다.
        // → enter 실패/조기 취소 시 불필요한 leave로 인원이 비대칭이 되는 것을 막는다
        //   (정리는 완료·에러·취소 모든 종료 신호에서 동일하게 1회 실행).
        // 입장/퇴장마다 정리 후 인원수를 같은 채널로 브로드캐스트해, 방의 모든 클라가 실시간 반영한다.
        return Mono.usingWhen(
                roomCounter.enter(key, memberId)
                        .doOnNext(c -> log.debug("WS enter account={} room={} count={}", accountId, roomId, c))
                        .flatMap(count -> publishPresence(channel, PresenceEvent.JOIN, count, accountId)
                                .thenReturn(count)),
                enteredCount -> Mono.firstWithSignal(session.send(outbound), inbound),
                enteredCount -> roomCounter.leave(key, memberId)
                        .doOnNext(c -> log.debug("WS leave account={} room={} count={}", accountId, roomId, c))
                        .flatMap(count -> publishPresence(channel, PresenceEvent.LEAVE, count, accountId)
                                .thenReturn(count))
                        .doOnError(e -> log.warn("leave/presence 실패 room={}: {}", roomId, e.getMessage()))
                        .onErrorComplete());
    }

    private String serialize(String accountId, String text) throws Exception {
        return objectMapper.writeValueAsString(new ChatMessage(accountId, text, Instant.now().toEpochMilli()));
    }

    /**
     * 입장/퇴장 presence 이벤트를 방 채널로 발행한다.
     *
     * <p>발행 실패는 삼켜서 커넥션 수명/카운터 대칭을 해치지 않는다: join 발행이 실패했다고 연결을
     * 끊으면 이미 INCR된 카운터의 leave(DECR)가 호출되지 않아 인원이 새기 때문이다(usingWhen 규약).
     */
    private Mono<Long> publishPresence(String channel, String event, long count, String accountId) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(
                        PresenceEvent.of(event, count, accountId, Instant.now().toEpochMilli())))
                .flatMap(payload -> broker.publish(channel, payload))
                .onErrorResume(e -> {
                    log.warn("presence 발행 실패 channel={} event={}: {}", channel, event, e.getMessage());
                    return Mono.empty();
                });
    }

    /** {@code Sec-WebSocket-Protocol} 헤더의 첫 값을 토큰으로 사용(쿼리 토큰 부재 시 폴백). */
    private String subProtocolToken(WebSocketSession session) {
        var values = session.getHandshakeInfo().getHeaders().get("Sec-WebSocket-Protocol");
        if (values == null || values.isEmpty()) {
            return null;
        }
        // "bearer, <token>" 형태로 오면 마지막 토큰만 취한다.
        String raw = values.get(0);
        int comma = raw.lastIndexOf(',');
        return (comma >= 0 ? raw.substring(comma + 1) : raw).trim();
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.putIfAbsent(name, value);
        }
        return result;
    }
}
