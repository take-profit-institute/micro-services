package org.profit.candle.trading.reservation.event;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import org.profit.candle.trading.support.event.MarketPriceEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Market 도메인의 현재가 Kafka 이벤트를 구독해 OPEN+MARKET 예약을 즉시 체결한다.
 *
 * <p>토픽명({@code TOPIC})과 그룹 ID({@code GROUP_ID})는 Market 담당자(팀장)와 협의 후
 * 확정 예정 — 상수 교체만으로 반영 가능하도록 분리했다.</p>
 *
 * <p>이벤트 수신 시 당일 scheduled_date의 OPEN+MARKET RESERVED 예약 전체를 체결한다.
 * 같은 이벤트가 중복 수신돼도 이미 RESERVED가 아닌 예약은 skip하므로 멱등하다.</p>
 *
 * <p>토픽은 orderbook 스냅샷과 섞지 않고 현재가 전용 {@code market.price.v1}을 사용한다.
 * payload는 {@code {"symbol":"005930","price":71400}} 형태다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationMarketPriceConsumer {

    private static final String GROUP_ID = "trading-service-reservation-market-price";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReservationBatchService reservationBatchService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @KafkaListener(topics = "${market.price.topic:market.price.v1}", groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, ?> record) {
        MarketPriceEvent event;
        try {
            event = parse(record.value());
        } catch (Exception e) {
            // 역직렬화 실패 = poison pill — 재시도해도 동일하게 실패하므로 로그만 남기고 skip.
            // value는 PII 포함 가능성 있어 로그에 포함하지 않는다.
            log.error("현재가 이벤트 역직렬화 실패 — offset={}, topic={}",
                    record.offset(), record.topic(), e);
            return;
        }

        try {
            log.info("현재가 이벤트 수신 — symbol={}, price={}", event.symbol(), event.price());

            // Clock 주입으로 KST 기준 오늘 날짜 계산 — LocalDate.now() 직접 호출 방지 (Qodo #3)
            LocalDate today = LocalDate.now(clock.withZone(KST));

            int count = reservationBatchService.processOpenMarketReservations(
                    today, event.symbol(), event.price());

            log.info("OPEN+MARKET 체결 완료 — symbol={}, price={}, count={}",
                    event.symbol(), event.price(), count);
        } catch (Exception e) {
            // 처리 실패(보상 실패 포함) — 예외를 재throw해 오프셋 커밋을 막고 재시도 유도 (Qodo #2).
            // 보상(releaseBalance) 실패도 여기서 잡혀 재시도되므로 영구 잔고 락을 방지한다.
            log.error("현재가 이벤트 처리 실패 — symbol={}, offset={}",
                    event.symbol(), record.offset(), e);
            throw new RuntimeException("OPEN+MARKET 체결 처리 실패 — symbol: " + event.symbol(), e);
        }
    }

    private MarketPriceEvent parse(Object payload) throws Exception {
        if (payload instanceof MarketPriceEvent event) {
            return event;
        }
        if (payload instanceof CharSequence json) {
            return objectMapper.readValue(json.toString(), MarketPriceEvent.class);
        }
        return objectMapper.convertValue(payload, MarketPriceEvent.class);
    }
}
