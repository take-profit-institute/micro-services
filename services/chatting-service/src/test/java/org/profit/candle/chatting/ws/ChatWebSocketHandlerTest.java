package org.profit.candle.chatting.ws;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.chatting.auth.HandshakeAuthenticator;
import org.profit.candle.chatting.room.RoomCounter;
import org.profit.candle.chatting.room.RoomKey;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
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
    ChatWebSocketHandler handler;

    WebSocketSession session;

    @BeforeEach
    void setUp() {
        authenticator = mock(HandshakeAuthenticator.class);
        roomCounter = mock(RoomCounter.class);
        broker = mock(ChatBroker.class);
        handler = new ChatWebSocketHandler(authenticator, roomCounter, broker, mock(ObjectMapper.class));

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
        // enter(자원 획득) 실패 → leave(DECR)가 호출되면 안 됨 (카운터 음수 방지)
        when(roomCounter.enter(any(RoomKey.class)))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        StepVerifier.create(handler.handle(session))
                .expectError(IllegalStateException.class)
                .verify();

        verify(roomCounter, never()).leave(any());
    }

    @Test
    void handle_clientDisconnects_leavesExactlyOnce() {
        // enter 성공 후 수신 종료(클라 끊김) → 정확히 1회 leave
        when(roomCounter.enter(any(RoomKey.class))).thenReturn(Mono.just(1L));
        when(roomCounter.leave(any(RoomKey.class))).thenReturn(Mono.just(0L));
        when(session.send(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        verify(roomCounter).enter(new RoomKey("005930", 1));
        verify(roomCounter).leave(new RoomKey("005930", 1));
    }

    @Test
    void handle_broadcastsPresenceOnEnterAndLeave() {
        // 입장 시 갱신 인원(join)과 퇴장 시 갱신 인원(leave)을 각각 방 채널로 발행한다.
        ChatWebSocketHandler realHandler =
                new ChatWebSocketHandler(authenticator, roomCounter, broker, new ObjectMapper());
        when(roomCounter.enter(any(RoomKey.class))).thenReturn(Mono.just(3L));
        when(roomCounter.leave(any(RoomKey.class))).thenReturn(Mono.just(2L));
        when(session.send(any())).thenReturn(Mono.empty());
        when(broker.publish(eq("chat:005930_1"), any())).thenReturn(Mono.just(1L));

        StepVerifier.create(realHandler.handle(session))
                .verifyComplete();

        verify(broker).publish(eq("chat:005930_1"), contains("\"event\":\"join\""));
        verify(broker).publish(eq("chat:005930_1"), contains("\"event\":\"leave\""));
    }

    @Test
    void handle_unauthorized_closesWithoutEntering() {
        when(authenticator.authenticate("tok")).thenReturn(Optional.empty());
        when(session.close(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        verify(roomCounter, never()).enter(any());
        verify(roomCounter, never()).leave(any());
    }
}
