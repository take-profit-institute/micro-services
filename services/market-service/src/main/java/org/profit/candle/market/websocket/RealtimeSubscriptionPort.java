package org.profit.candle.market.websocket;

import java.util.Collection;

/**
 * SubscriptionManager 가 WS 클라이언트에게 요구하는 최소 계약.
 * 재조정(REG/REMOVE)에 필요한 것만 노출해 결합을 좁히고 단위 테스트를 쉽게 한다.
 */
public interface RealtimeSubscriptionPort {

    boolean isConnected();

    /** 현재 연결에 심볼을 실시간 등록(REG). 연결이 없으면 무시. */
    void register(Collection<String> symbols);

    /** 현재 연결에서 심볼 실시간 등록 해제(REMOVE). 연결이 없으면 무시. */
    void unregister(Collection<String> symbols);

    void setRegistrationListener(RealtimeRegistrationListener listener);
}
