package org.profit.candle.chatting.room;

import reactor.core.publisher.Mono;

/**
 * 방 인원 카운터(WS 연결 핸들러 소비자용).
 *
 * <p>인원수의 권원(authority)은 실제 WS 연결/해제다. onClose/onError에서 {@link #leave}
 * 누락 시 "유저 없는데 방이 꽉 찼다"고 오인되므로 반드시 호출한다.
 */
public interface RoomCounter {

    /** 연결 시 INCR. 증가 후 값을 반환. */
    Mono<Long> enter(RoomKey key);

    /** 해제 시 DECR. 감소 후 값을 반환. */
    Mono<Long> leave(RoomKey key);
}
