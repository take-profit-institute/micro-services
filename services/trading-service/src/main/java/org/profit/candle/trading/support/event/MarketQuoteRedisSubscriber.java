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
import org.springframework.stereotype.Component;

/**
 * market-service가 발행하는 {@code market:quotes} Redis Pub/Sub 채널을 구독해
 * 기존 {@code OrderMarketPriceConsumer}(Kafka) + {@code ReservationMarketPriceConsumer}(Kafka)
 * 두 개를 대체한다.
 *
 * <p>market-service는 Kafka를 전혀 사용하지 않고 Redis Pub/Sub만 사용한다 — 기존
 * {@code market.order-book.v1} Kafka 토픽은 발행 주체가 없어 영구히 수신되지 않았다.
 * wishlist-service가 이미 동일 채널을 {@code RedisMarketQuoteSubscriber}로 구독 중이며,
 * 이 클래스는 그 구조를 그대로 미러링한다.</p>
 *
 * <p>수행 순서:
 * 1. {@link CachedMarketPriceProvider} 캐시 갱신 — 시장가 즉시 체결(EXE-001)에서 사용
 * 2. {@link OrderExecutionService}로 PENDING 지정가 조건 체결(EXE-002)
 * 3. {@link ReservationBatchService}로 당일 OPEN+MARKET RESERVED 예약 체결
 * </p>
 *
 * <p><b>Kafka와의 차이 — 재시도 없음:</b> Redis Pub/Sub은 컨슈머 그룹/오프셋 개념이 없어
 * 발행 시점에 구독자가 없거나 처리 중 예외가 나도 재전달되지 않는다(at-most-once).
 * 기존 Kafka 컨슈머의 "예외 재throw → 오프셋 커밋 차단 → 재시도" 패턴은 여기서 의미가 없다.
 * 시세는 초 단위로 계속 재발행되고 아래 로직이 모두 멱등하게 짜여 있어(RESERVED가 아니면
 * skip 등) 실질적인 정합성 문제는 없지만, 이 특성 변화는 리뷰에서 명시적으로 인지해야 한다.</p>
 */
@Slf4j
@Component
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
            // 다음 tick이 곧 다시 들어오고 위 로직이 멱등하므로 실질적 영향은 제한적이다.
            log.error("현재가 이벤트 처리 실패 — symbol={}", tick.symbol(), e);
        }
    }
}