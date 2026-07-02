package org.profit.candle.chatting.room;

/**
 * 방 배정 결과(REST 응답). 클라이언트는 {@code roomId}로 WS 커넥션을 맺는다.
 *
 * @param symbol  종목코드 (예: 005930)
 * @param room    방 번호
 * @param roomId  {@code {symbol}_{room}} — WS 쿼리 {@code ?room=} 값
 * @param channel Redis Pub/Sub 채널 (디버깅/모니터링 참고용)
 * @param count   배정 시점의 방 인원(대략치, 낙관적)
 */
public record RoomAssignment(String symbol, int room, String roomId, String channel, long count) {

    static RoomAssignment of(RoomKey key, long count) {
        return new RoomAssignment(key.symbol(), key.room(), key.roomId(), key.channel(), count);
    }
}
