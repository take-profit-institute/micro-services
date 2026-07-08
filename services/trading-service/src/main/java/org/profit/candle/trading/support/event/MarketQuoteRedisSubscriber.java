package org.profit.candle.trading.support.event;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.trading.order.service.CachedMarketPriceProvider;
import org.profit.candle.trading.order.service.OrderExecutionService;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * market-service가 발행하는 {@code market:quotes} Redis Pub/Sub 채널을 구독하는 구현체.
 *
 * <p><b>[2026-07-08 현황] 현재 비활성화됨 — {@code @Component} 제거.</b> Redis/Kafka 두 구현체
 * 중 팀장 결정으로 Kafka 경로({@code OrderMarketPriceConsumer}/{@code ReservationMarketPriceConsumer})만
 * 활성화하기로 함. 이 클래스는 삭제하지 않고 대안 구현으로 남겨두되, Spring 빈으로 등록되지
 * 않으므로 실제로는 아무 메시지도 처리하지 않는다.</p>
 *
 * <p>다시 활성화하려면: 이 클래스에 {@code @Component}를 복원하고, {@code RedisListenerConfig}의
 * {@code @Configuration}도 함께 복원해야 한다 — 그래야 {@code RedisMessageListenerContainer}가
 * 이 subscriber를 주입받아 채널을 구독한다. 둘 중 하나만 켜면 Spring 컨텍스트 시작 시
 * "MarketQuoteRedisSubscriber 빈을 찾을 수 없다"는 에러로 부팅이 실패한다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class MarketQuoteRedisSubscriber implements MessageListener {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketQuoteMessageParser parser;
    private final CachedMarketPriceProvider cachedMarketPriceProvider;
    private final OrderExecutionService orderExecutionService;
    private final ReservationBatchService reservationBatchService;
    private final Clock clock;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        MarketQuoteTick tick;
        try {
            tick = parser.parse(payload);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid market quote payload");
            return;
        }

        if (tick.symbol() == null || tick.symbol().isBlank()) {
            log.error("현재가 이벤트 symbol 누락");
            return;
        }
        if (tick.price() <= 0) {
            log.error("현재가 이벤트 유효하지 않은 price — symbol={}, price={}", tick.symbol(), tick.price());
            return;
        }

        try {
            // 1. 캐시 갱신 — 시장가 즉시 체결(EXE-001)에서 getCurrentPriceKrw()가 읽는 값
            cachedMarketPriceProvider.updatePrice(tick.symbol(), tick.price());

            // 2. 지정가 조건 체결(EXE-002) — 방금 갱신된 가격으로 PENDING 지정가 주문 스캔
            int filledOrders = orderExecutionService.fillLimitOrdersIfConditionMet(
                    tick.symbol(), tick.price());
            if (filledOrders > 0) {
                log.info("지정가 조건 체결 완료 — symbol={}, price={}, count={}",
                        tick.symbol(), tick.price(), filledOrders);
            }

            // 3. OPEN+MARKET 예약 체결 — 당일 scheduled_date 기준
            LocalDate today = LocalDate.now(clock.withZone(KST));
            int filledReservations = reservationBatchService.processOpenMarketReservations(
                    today, tick.symbol(), tick.price());
            if (filledReservations > 0) {
                log.info("OPEN+MARKET 예약 체결 완료 — symbol={}, price={}, count={}",
                        tick.symbol(), tick.price(), filledReservations);
            }
        } catch (RuntimeException e) {
            // Redis Pub/Sub은 재전달이 없다 — 여기서 던져도 재시도되지 않으므로 로그만 남긴다.
            log.error("현재가 이벤트 처리 실패 — symbol={}", tick.symbol(), e);
        }
    }
}