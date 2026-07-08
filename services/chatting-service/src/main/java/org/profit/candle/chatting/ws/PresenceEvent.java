package org.profit.candle.chatting.ws;

/**
 * 입장/퇴장 시 방 전체로 브로드캐스트되는 presence 이벤트.
 *
 * <p>채팅 메시지 봉투({@link ChatMessage} = {@code {accountId, message, ts}})와 같은 채널로 나가므로,
 * 클라이언트가 구분할 수 있도록 {@code type="presence"} 판별자를 갖는다. 판별자가 없는 프레임은
 * 채팅 메시지로 간주된다(구버전 클라는 message 필드가 없는 이 프레임을 무시 → 하위호환).
 *
 * @param type      항상 {@code "presence"} (클라 판별자)
 * @param event     {@code "join"} 또는 {@code "leave"}
 * @param count     이벤트 처리 후 방 인원(권원: Redis 카운터)
 * @param accountId 입장/퇴장한 유저
 * @param ts        서버 시각(epoch millis)
 */
public record PresenceEvent(String type, String event, long count, String accountId, long ts) {

    public static final String TYPE = "presence";
    public static final String JOIN = "join";
    public static final String LEAVE = "leave";

    public static PresenceEvent of(String event, long count, String accountId, long ts) {
        return new PresenceEvent(TYPE, event, count, accountId, ts);
    }
}
