package org.profit.candle.chatting.ws;

/**
 * 브로드캐스트되는 채팅 메시지 봉투. 영속 저장하지 않으며 실시간 전달용이다.
 *
 * @param accountId 보낸 유저(핸드셰이크에서 확정)
 * @param message   원문 텍스트
 * @param ts        서버 수신 시각(epoch millis)
 */
public record ChatMessage(String accountId, String message, long ts) {
}
