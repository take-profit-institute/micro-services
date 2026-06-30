package org.profit.candle.chatting.room;

/**
 * 방 식별자. roomId 포맷은 {@code {symbol}_{room}} (예: {@code 005930_1}).
 *
 * <ul>
 *   <li>Pub/Sub 채널: {@code chat:{symbol}_{room}}</li>
 *   <li>인원 카운터 키: {@code {symbol}_{room}_count}</li>
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

    public String countKey() {
        return roomId() + "_count";
    }
}
