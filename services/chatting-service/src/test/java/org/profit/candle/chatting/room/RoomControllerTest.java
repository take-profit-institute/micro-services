package org.profit.candle.chatting.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomControllerTest {

    RoomAssigner roomAssigner;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        roomAssigner = mock(RoomAssigner.class);
        client = WebTestClient.bindToController(new RoomController(roomAssigner)).build();
    }

    @Test
    void assign_returnsRoomAssignment() {
        when(roomAssigner.assign("005930")).thenReturn(Mono.just(
                new RoomAssignment("005930", 1, "005930_1", "chat:005930_1", 10L)));

        client.get()
                .uri("/chat/rooms?symbol=005930")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("005930")
                .jsonPath("$.room").isEqualTo(1)
                .jsonPath("$.roomId").isEqualTo("005930_1")
                .jsonPath("$.channel").isEqualTo("chat:005930_1")
                .jsonPath("$.count").isEqualTo(10);
    }
}
