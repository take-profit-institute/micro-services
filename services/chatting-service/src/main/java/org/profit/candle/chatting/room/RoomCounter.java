package org.profit.candle.chatting.room;

import reactor.core.publisher.Mono;

/**
 * 방 인원 카운터(WS 연결 핸들러 소비자용).
 *
 * <p>인원의 권원(authority)은 presence ZSET이다: member=커넥션 식별자, score=마지막 heartbeat 시각.
 * 살아있는 커넥션은 {@link #heartbeat}로 주기적으로 score를 갱신하고, 비정상 종료로 갱신이 끊긴
 * 멤버는 {@code presenceTtl}이 지나면 카운트에서 제외된다(자가치유). 따라서 {@link #leave} 누락이
 * 있어도 인원이 영구히 새지 않는다. 모든 카운트 조회는 만료분을 먼저 정리한 뒤 집계한다.
 *
 * @implNote 반환하는 count는 정리 후의 활성 멤버 수(대략치, 낙관적).
 */
public interface RoomCounter {

    /** 입장 — presence에 커넥션 등록(ZADD) 후 활성 인원 반환. */
    Mono<Long> enter(RoomKey key, String memberId);

    /** heartbeat — presence score 갱신(ZADD)으로 만료 방지. 정리 후 활성 인원 반환. */
    Mono<Long> heartbeat(RoomKey key, String memberId);

    /** 퇴장(WS 정상 종료) — presence에서 커넥션 제거(ZREM) 후 활성 인원 반환. */
    Mono<Long> leave(RoomKey key, String memberId);
}
