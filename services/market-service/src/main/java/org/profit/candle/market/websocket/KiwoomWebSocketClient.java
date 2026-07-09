package org.profit.candle.market.websocket;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.client.KiwoomAuthClient;
import org.profit.candle.market.dto.LiveQuote;
import org.profit.candle.market.dto.message.MarketQuoteMessage;
import org.profit.candle.market.dto.message.StockPriceMessage;
import org.profit.candle.market.entity.Tick;
import org.profit.candle.market.publisher.MarketQuotePublisher;
import org.profit.candle.market.publisher.StockPricePublisher;
import org.profit.candle.market.repository.TickRepository;
import org.profit.candle.market.session.MarketSession;
import org.profit.candle.market.stream.LiveQuoteSink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 키움 실시간 시세 WebSocket 클라이언트.
 *
 * 연결 수명은 세션 게이팅 스케줄러({@link KiwoomWebSocketSupervisor})가, 구독 심볼 집합은
 * SubscriptionManager({@link RealtimeRegistrationListener})가 소유한다. 여기서는 연결/로그인과
 * REG/REMOVE 프레임 송신만 담당한다.
 *
 * 키움 실시간 등록은 커넥션 종속 상태라, 로그인 직후 desired 전체를 다시 등록한다
 * ({@link RealtimeRegistrationListener#onConnectionReady()}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomWebSocketClient implements RealtimeSubscriptionPort {

    private static final URI WS_URI = URI.create("wss://api.kiwoom.com:10000/api/dostk/websocket");

    private enum State { DISCONNECTED, CONNECTING, CONNECTED }

    private final KiwoomAuthClient kiwoomAuthClient;
    private final StockPricePublisher stockPricePublisher;
    private final MarketQuotePublisher marketQuotePublisher;
    private final MarketSession marketSession;
    private final TickRepository tickRepository;
    private final LiveQuoteSink liveQuoteSink;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringBuilder messageBuffer = new StringBuilder();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
    private volatile WebSocket webSocket;
    private volatile RealtimeRegistrationListener registrationListener;

    @Value("${market.ws.max-symbols-per-group:100}")
    private int maxSymbolsPerGroup;
    private SubscriptionGroups groups;

    @PostConstruct
    void initGroups() {
        groups = new SubscriptionGroups(maxSymbolsPerGroup);
    }

    @Override
    public void setRegistrationListener(RealtimeRegistrationListener listener) {
        this.registrationListener = listener;
    }

    @Override
    public boolean isConnected() {
        return state.get() == State.CONNECTED;
    }

    /** 연결이 없을 때만 새로 붙는다. 이미 연결/연결중이면 아무 것도 하지 않는다(멱등). */
    public void ensureConnected() {
        if (!state.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
            return;
        }
        try {
            String token = kiwoomAuthClient.issueToken().token();
            httpClient.newWebSocketBuilder()
                    .buildAsync(WS_URI, new KiwoomListener(token))
                    .whenComplete((ws, err) -> {
                        if (err != null) {
                            log.warn("키움 WebSocket 연결 실패", err);
                            state.compareAndSet(State.CONNECTING, State.DISCONNECTED);
                        } else if (state.compareAndSet(State.CONNECTING, State.CONNECTED)) {
                            webSocket = ws;
                        } else {
                            // 연결 도중 해제가 요청됨 → 방금 열린 소켓을 곧바로 닫는다
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "aborted");
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("키움 WebSocket 연결 준비 실패(토큰 등)", e);
            state.set(State.DISCONNECTED);
        }
    }

    /** 연결을 정상 종료한다(멱등). 창 밖(장 마감 후)에서 스케줄러가 호출한다. */
    public void ensureDisconnected() {
        State prev = state.getAndSet(State.DISCONNECTED);
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null && prev == State.CONNECTED) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "session end");
            log.info("키움 WebSocket 세션 종료");
        }
        notifyConnectionLost();
    }

    @Override
    public void register(Collection<String> symbols) {
        WebSocket ws = webSocket;
        if (ws == null || symbols.isEmpty()) {
            return;
        }
        synchronized (groups) {
            groups.assign(symbols).forEach((grpNo, syms) -> sendFrame(ws, "REG", grpNo, syms));
        }
    }

    @Override
    public void unregister(Collection<String> symbols) {
        WebSocket ws = webSocket;
        if (ws == null || symbols.isEmpty()) {
            return;
        }
        synchronized (groups) {
            groups.release(symbols).forEach((grpNo, syms) -> sendFrame(ws, "REMOVE", grpNo, syms));
        }
    }

    private void onDisconnected() {
        webSocket = null;
        state.set(State.DISCONNECTED);
        notifyConnectionLost();
    }

    private void notifyConnectionLost() {
        RealtimeRegistrationListener listener = registrationListener;
        if (listener != null) {
            listener.onConnectionLost();
        }
    }

    private final class KiwoomListener implements WebSocket.Listener {

        private final String token;

        private KiwoomListener(String token) {
            this.token = token;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("키움 WebSocket 연결 성공");
            // 로그인 응답 처리 시점에 register 가 쓸 수 있도록 소켓 참조를 미리 확보한다
            KiwoomWebSocketClient.this.webSocket = webSocket;
            String loginRequest = """
                    {
                      "trnm": "LOGIN",
                      "token": "%s"
                    }
                    """.formatted(token);
            webSocket.sendText(loginRequest, true);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (!last) {
                return WebSocket.Listener.super.onText(webSocket, data, false);
            }

            String message = messageBuffer.toString();
            messageBuffer.setLength(0);

            try {
                JsonNode root = objectMapper.readTree(message);
                String trnm = root.path("trnm").asText();

                // 키움 heartbeat — PING 수신 시 받은 메시지를 그대로 되돌려줘야 연결이 유지된다.
                // 응답하지 않으면 서버가 R10002로 접속을 종료한다.
                if ("PING".equals(trnm)) {
                    webSocket.sendText(message, true);
                    return WebSocket.Listener.super.onText(webSocket, data, true);
                }

                if ("LOGIN".equals(trnm)) {
                    if (root.path("return_code").asInt() == 0) {
                        log.info("키움 WebSocket 로그인 성공");
                        registerDesiredOnLogin(webSocket);
                    }
                    return WebSocket.Listener.super.onText(webSocket, data, true);
                }

                if ("REAL".equals(trnm)) {
                    handleRealtimeMessage(root);
                }
            } catch (Exception e) {
                log.warn("키움 WebSocket 메시지 처리 실패", e);
            }

            return WebSocket.Listener.super.onText(webSocket, data, true);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("키움 WebSocket 종료 status={} reason={}", statusCode, reason);
            onDisconnected();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("키움 WebSocket 오류", error);
            onDisconnected();
        }
    }

    /** 로그인 직후 SubscriptionManager 의 desired 집합 전체를 grp_no 로 샤딩해 재등록한다. */
    private void registerDesiredOnLogin(WebSocket webSocket) {
        RealtimeRegistrationListener listener = registrationListener;
        Collection<String> desired = listener != null ? listener.onConnectionReady() : List.of();
        synchronized (groups) {
            // 재연결 → 서버 등록 소실. 그룹 배정을 새로 잡고 그룹별로 REG.
            groups.reset();
            groups.assign(desired).forEach((grpNo, syms) -> sendFrame(webSocket, "REG", grpNo, syms));
        }
    }

    private void sendFrame(WebSocket webSocket, String trnm, String grpNo, Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return;
        }
        try {
            String request = objectMapper.writeValueAsString(
                    new KiwoomRealtimeRequest(trnm, grpNo, symbols));
            webSocket.sendText(request, true);
            log.info("실시간 {} grp={} 요청 = {}", trnm, grpNo, request);
        } catch (Exception e) {
            log.warn("실시간 {} grp={} 요청 실패", trnm, grpNo, e);
        }
    }

    private void handleRealtimeMessage(JsonNode root) {
        JsonNode dataArray = root.path("data");

        for (JsonNode itemNode : dataArray) {
            String stockCode = itemNode.path("item").asText();
            JsonNode values = itemNode.path("values");

            long currentPrice = parseAbsInt(values.path("10").asText());   // 10: 현재가
            long openPrice = parseAbsInt(values.path("16").asText());       // 16: 시가
            long change = parseInt(values.path("11").asText());            // 11: 전일 대비
            double changeRate = parseDouble(values.path("12").asText());   // 12: 등락률
            long volume = parseLong(values.path("13").asText());           // 13: 누적 거래량
            String sign = values.path("25").asText();                      // 25: 전일대비 부호
            String tickTime = values.path("20").asText();                  // 20: 체결시각 HHMMSS
            Instant tickedAt = marketSession.timestampOf(tickTime);

            Tick tick = Tick.builder()
                    .stockCode(stockCode)
                    .currentPrice(currentPrice)
                    .priceChange(change)
                    .priceChangeRate(BigDecimal.valueOf(changeRate))
                    .priceChangeSign(sign)
                    .tradingVolume(volume)
                    .tickedAt(OffsetDateTime.now())
                    .collectedAt(OffsetDateTime.now())
                    .build();

            tickRepository.save(tick);

            StockPriceMessage message = new StockPriceMessage(
                    stockCode,
                    null,
                    (int) currentPrice,
                    (int) change,
                    changeRate,
                    volume,
                    tick.getTickedAt().toString()
            );

            stockPricePublisher.publish(message);

            // wishlist ±5% 판정용 공급 (market:quotes) — 시가 포함, @class 없는 순수 JSON
            marketQuotePublisher.publish(new MarketQuoteMessage(
                    stockCode,
                    currentPrice,
                    openPrice,
                    marketSession.status(),
                    marketSession.tradingDate(),
                    tickedAt
            ));


            // 종목 상세 뷰어 팬아웃 (gRPC StreamQuotes)
            liveQuoteSink.publish(new LiveQuote(
                    stockCode,
                    currentPrice,
                    change,
                    changeRate,
                    openPrice,
                    volume,
                    sign,
                    tickedAt
            ));
        }
    }

    private int parseAbsInt(String value) {
        return Math.abs(parseInt(value));
    }

    private int parseInt(String value) {
        return Integer.parseInt(value.replace("+", "").replace(",", "").trim());
    }

    private long parseLong(String value) {
        return Long.parseLong(value.replace("+", "").replace(",", "").trim());
    }

    private double parseDouble(String value) {
        return Double.parseDouble(value.replace("+", "").replace("%", "").replace(",", "").trim());
    }

    private record KiwoomRealtimeRequest(
            String trnm,
            String grp_no,
            String refresh,
            List<KiwoomRealtimeData> data
    ) {
        KiwoomRealtimeRequest(String trnm, String grpNo, Collection<String> symbols) {
            this(
                    trnm,
                    grpNo,
                    "1",
                    List.of(new KiwoomRealtimeData(List.copyOf(symbols), List.of("0B")))
            );
        }
    }

    private record KiwoomRealtimeData(
            List<String> item,
            List<String> type
    ) {
    }
}
