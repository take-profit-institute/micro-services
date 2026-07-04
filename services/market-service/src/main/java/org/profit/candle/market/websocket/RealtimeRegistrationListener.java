package org.profit.candle.market.websocket;

import java.util.Collection;

/**
 * WS 연결 수명 이벤트를 구독 상태 소유자(SubscriptionManager)에게 알리는 콜백.
 *
 * 키움 실시간 등록은 커넥션에 종속된 서버 상태라 연결이 끊기면 소실된다. 따라서 재연결·로그인
 * 직후 desired 집합 전체를 다시 등록해야 한다.
 */
public interface RealtimeRegistrationListener {

    /** 로그인 완료 시 호출. 지금 등록해야 할 desired 심볼 집합을 반환한다. */
    Collection<String> onConnectionReady();

    /** 연결이 끊겼을 때 호출. 등록 상태를 비운다(다음 연결에서 전체 재등록). */
    void onConnectionLost();
}
