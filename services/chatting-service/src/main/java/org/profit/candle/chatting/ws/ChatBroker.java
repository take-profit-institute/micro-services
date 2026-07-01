package org.profit.candle.chatting.ws;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 방 단위 메시지 팬아웃. 같은 채널을 구독한 모든 인스턴스가 PUBLISH를 수신하므로
 * 유저가 어느 인스턴스에 붙어 있든 메시지가 공유된다(sticky session 불필요).
 */
public interface ChatBroker {

    /** 채널 구독 스트림(커넥션 단위로 살아있다가 unsubscribe 시 정리). */
    Flux<String> subscribe(String channel);

    /** 채널로 메시지 발행. 반환값은 수신 구독자 수. */
    Mono<Long> publish(String channel, String payload);
}
