package org.profit.candle.market.websocket;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.client.KiwoomAuthClient;
import org.profit.candle.market.dto.message.StockPriceMessage;
import org.profit.candle.market.publisher.StockPricePublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;

@Component
@RequiredArgsConstructor
public class KiwoomWebSocketClient {

    private final KiwoomAuthClient kiwoomAuthClient;
    private final StockPricePublisher stockPricePublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void connect(List<String> stockCodes) {
        String token = kiwoomAuthClient.issueToken().token();

        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("wss://api.kiwoom.com:10000/api/dostk/websocket"),
                        new WebSocket.Listener() {

                            @Override
                            public void onOpen(WebSocket webSocket) {
                                System.out.println("키움 WebSocket 연결 성공");

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
                                String message = data.toString();
                                System.out.println("수신 메시지 = " + message);

                                try {
                                    JsonNode root = objectMapper.readTree(message);
                                    String trnm = root.path("trnm").asText();

                                    if ("LOGIN".equals(trnm)) {
                                        if (root.path("return_code").asInt() == 0) {
                                            System.out.println("키움 WebSocket 로그인 성공");
                                            sendRegisterMessage(webSocket, stockCodes);
                                        }
                                        return WebSocket.Listener.super.onText(webSocket, data, last);
                                    }

                                    if ("REAL".equals(trnm)) {
                                        handleRealtimeMessage(root);
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                return WebSocket.Listener.super.onText(webSocket, data, last);
                            }

                            @Override
                            public void onError(WebSocket webSocket, Throwable error) {
                                error.printStackTrace();
                            }
                        }
                );
    }

    private void sendRegisterMessage(WebSocket webSocket, List<String> stockCodes) {
        try {
            String request = objectMapper.writeValueAsString(new KiwoomRealtimeRegisterRequest(stockCodes));
            webSocket.sendText(request, true);
            System.out.println("실시간 종목 등록 요청 = " + request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleRealtimeMessage(JsonNode root) {
        JsonNode dataArray = root.path("data");

        for (JsonNode itemNode : dataArray) {
            String stockCode = itemNode.path("item").asText();
            JsonNode values = itemNode.path("values");

            StockPriceMessage message = new StockPriceMessage(
                    stockCode,
                    null,
                    parseAbsInt(values.path("10").asText()),
                    parseInt(values.path("11").asText()),
                    parseDouble(values.path("12").asText()),
                    parseLong(values.path("13").asText())
            );

            stockPricePublisher.publish(message);
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

    private record KiwoomRealtimeRegisterRequest(
            String trnm,
            String grp_no,
            String refresh,
            List<KiwoomRealtimeRegisterData> data
    ) {
        KiwoomRealtimeRegisterRequest(List<String> stockCodes) {
            this(
                    "REG",
                    "1",
                    "1",
                    List.of(new KiwoomRealtimeRegisterData(stockCodes, List.of("0B")))
            );
        }
    }

    private record KiwoomRealtimeRegisterData(
            List<String> item,
            List<String> type
    ) {
    }
}
