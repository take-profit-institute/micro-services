package org.profit.candle.chatting.room;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 방 배정 REST. 게이트웨이가 JWT 검증 후 라우팅한다.
 *
 * <pre>GET /chat/rooms?symbol=005930 → { roomId: "005930_1", ... }</pre>
 *
 * <p>컨벤션 예외(CONVENTIONS §6): 게이트웨이가 직접 라우팅하는 클라이언트 대면 진입점.
 */
@RestController
@RequestMapping("/chat/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomAssigner roomAssigner;

    @GetMapping
    public Mono<RoomAssignment> assign(@RequestParam String symbol) {
        return roomAssigner.assign(symbol);
    }
}
