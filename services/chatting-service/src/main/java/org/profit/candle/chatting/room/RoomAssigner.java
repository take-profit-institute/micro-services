package org.profit.candle.chatting.room;

import reactor.core.publisher.Mono;

/**
 * 종목 입장 요청에 방을 배정한다(REST 소비자용).
 *
 * <p>덜 찬 방을 고르거나, 모두 정원이면 새 방을 발급한다. 카운터는 읽기만 한다
 * (인원 증감의 권원은 {@link RoomCounter}의 WS 연결/해제다).
 */
public interface RoomAssigner {

    Mono<RoomAssignment> assign(String symbol);
}
