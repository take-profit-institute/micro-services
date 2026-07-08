package org.profit.candle.chatting.room;

/**
 * 방 식별자. roomId 포맷은 {@code {symbol}_{room}} (예: {@code 005930_1}).
 *
 * <ul>
 *   <li>Pub/Sub 채널: {@code chat:{symbol}_{room}}</li>
 *   <li>presence 키(ZSET, member=커넥션, score=last-seen): {@code {symbol}_{room}_presence}</li>
 * </ul>
 */
public record RoomKey(String symbol, int room) {

    public static RoomKey parse(String roomId) {
        int sep = roomId.lastIndexOf('_');
        if (sep <= 0 || sep == roomId.length() - 1) {
            throw new IllegalArgumentException("잘못된 roomId 포맷: " + roomId);
        }
        String symbol = roomId.substring(0, sep);
        int room = Integer.parseInt(roomId.substring(sep + 1));
        return new RoomKey(symbol, room);
    }

    public String roomId() {
        return symbol + "_" + room;
    }

    public String channel() {
        return "chat:" + roomId();
    }

    /** presence ZSET 키. member=커넥션 식별자, score=마지막 heartbeat epoch millis. */
    public String presenceKey() {
        return roomId() + "_presence";
    }
}
