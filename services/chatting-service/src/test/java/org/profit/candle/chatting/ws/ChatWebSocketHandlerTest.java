package org.profit.candle.chatting.ws;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.profit.candle.chatting.auth.HandshakeAuthenticator;
import org.profit.candle.chatting.config.ChatProperties;
import org.profit.candle.chatting.room.RoomCounter;
import org.profit.candle.chatting.room.RoomKey;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    HandshakeAuthenticator authenticator;
    RoomCounter roomCounter;
    ChatBroker broker;
    ChatProperties properties;
    ChatWebSocketHandler handler;

    WebSocketSession session;

    @BeforeEach
    void setUp() {
        authenticator = mock(HandshakeAuthenticator.class);
        roomCounter = mock(RoomCounter.class);
        broker = mock(ChatBroker.class);
        // heartbeat 주기는 길게 잡는다 — 단위 테스트에선 outbound가 구독되지 않아 실제로 틱하지 않는다.
        properties = new ChatProperties(
                new ChatProperties.Jwt("http://unused/.well-known/jwks.json", "iss", "aud"),
                new ChatProperties.Room(500, Duration.ofMinutes(1), Duration.ofSeconds(30)),
                new ChatProperties.Cors(List.of()));
        handler = new ChatWebSocketHandler(authenticator, roomCounter, broker, mock(ObjectMapper.class), properties);

        session = mock(WebSocketSession.class);
        HandshakeInfo info = mock(HandshakeInfo.class);
        when(session.getHandshakeInfo()).thenReturn(info);
        when(info.getUri()).thenReturn(URI.create("ws://host/chat/ws?room=005930_1&token=tok"));
        when(authenticator.authenticate("tok")).thenReturn(Optional.of("account-1"));
        when(broker.subscribe("chat:005930_1")).thenReturn(Flux.empty());
        when(session.receive()).thenReturn(Flux.<WebSocketMessage>empty());
    }

    @Test
    void handle_enterFails_doesNotLeave() {
        // enter(자원 획득) 실패 → leave(정리)가 호출되면 안 됨 (인원 비대칭 방지)
        when(roomCounter.enter(any(RoomKey.class), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        StepVerifier.create(handler.handle(session))
                .expectError(IllegalStateException.class)
                .verify();

        verify(roomCounter, never()).leave(any(), anyString());
    }

    @Test
    void handle_clientDisconnects_leavesExactlyOnce() {
        // enter 성공 후 수신 종료(클라 끊김) → 정확히 1회 leave
        when(roomCounter.enter(any(RoomKey.class), anyString())).thenReturn(Mono.just(1L));
        when(roomCounter.leave(any(RoomKey.class), anyString())).thenReturn(Mono.just(0L));
        when(session.send(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        verify(roomCounter).enter(eq(new RoomKey("005930", 1)), anyString());
        verify(roomCounter).leave(eq(new RoomKey("005930", 1)), anyString());
    }

    @Test
    void handle_broadcastsPresenceOnEnterAndLeave() {
        // 입장 시 갱신 인원(join)과 퇴장 시 갱신 인원(leave)을 각각 방 채널로 발행한다.
        ChatWebSocketHandler realHandler =
                new ChatWebSocketHandler(authenticator, roomCounter, broker, new ObjectMapper(), properties);
        when(roomCounter.enter(any(RoomKey.class), anyString())).thenReturn(Mono.just(3L));
        when(roomCounter.leave(any(RoomKey.class), anyString())).thenReturn(Mono.just(2L));
        when(session.send(any())).thenReturn(Mono.empty());
        when(broker.publish(eq("chat:005930_1"), any())).thenReturn(Mono.just(1L));

        StepVerifier.create(realHandler.handle(session))
                .verifyComplete();

        verify(broker).publish(eq("chat:005930_1"), contains("\"event\":\"join\""));
        verify(broker).publish(eq("chat:005930_1"), contains("\"event\":\"leave\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handle_sendsCurrentCountToJoinerAsFirstFrame() {
        // 입장자는 자기 JOIN 브로드캐스트를 놓치므로, outbound 첫 프레임으로 현재 인원을 즉시 받아야 한다.
        ChatWebSocketHandler realHandler =
                new ChatWebSocketHandler(authenticator, roomCounter, broker, new ObjectMapper(), properties);
        when(roomCounter.enter(any(RoomKey.class), anyString())).thenReturn(Mono.just(7L));
        when(roomCounter.leave(any(RoomKey.class), anyString())).thenReturn(Mono.just(6L));
        when(broker.publish(eq("chat:005930_1"), any())).thenReturn(Mono.just(1L));
        when(session.textMessage(anyString())).thenAnswer(inv -> {
            WebSocketMessage m = mock(WebSocketMessage.class);
            when(m.getPayloadAsText()).thenReturn(inv.getArgument(0));
            return m;
        });
        ArgumentCaptor<Publisher<WebSocketMessage>> outbound = ArgumentCaptor.forClass(Publisher.class);
        when(session.send(outbound.capture())).thenReturn(Mono.empty());

        StepVerifier.create(realHandler.handle(session)).verifyComplete();

        // 캡처한 outbound의 첫 프레임 = 본인 현재 인원(count=7)인 presence join.
        StepVerifier.create(Flux.from(outbound.getValue()).map(WebSocketMessage::getPayloadAsText).take(1))
                .expectNextMatches(s -> s.contains("\"event\":\"join\"") && s.contains("\"count\":7"))
                .verifyComplete();
    }

    @Test
    void handle_unauthorized_closesWithoutEntering() {
        when(authenticator.authenticate("tok")).thenReturn(Optional.empty());
        when(session.close(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        verify(roomCounter, never()).enter(any(), anyString());
        verify(roomCounter, never()).leave(any(), anyString());
    }
}
