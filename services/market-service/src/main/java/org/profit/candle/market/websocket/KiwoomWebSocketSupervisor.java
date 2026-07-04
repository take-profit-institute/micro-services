package org.profit.candle.market.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.session.MarketSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WS 세션 게이팅.
 *
 * 주기적으로 "지금 붙어 있어야 하는가"(거래일 연결창)를 실제 연결 상태와 맞춘다. 창 진입 시 연결,
 * 창 이탈 시 해제, 창 안에서 끊겼으면 재연결까지 한 루프로 처리한다. 예전 ApplicationReadyEvent
 * 영구 연결을 대체한다 — 주말/야간에 불필요하게 붙어 있지 않는다.
 *
 * 구독 심볼 집합은 SubscriptionManager 가 소유한다. 여기서는 연결 수명만 다룬다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomWebSocketSupervisor {

    private final KiwoomWebSocketClient client;
    private final MarketSession marketSession;

    // 로컬 개발 등 장시간 밖에서도 강제로 연결하고 싶을 때 사용
    @Value("${market.ws.force-connect:false}")
    private boolean forceConnect;

    @Scheduled(
            initialDelayString = "${market.ws.supervise-interval-ms:30000}",
            fixedDelayString = "${market.ws.supervise-interval-ms:30000}")
    public void supervise() {
        boolean shouldConnect = forceConnect || marketSession.withinConnectionWindow();
        if (shouldConnect) {
            client.ensureConnected();
        } else {
            client.ensureDisconnected();
        }
    }
}
