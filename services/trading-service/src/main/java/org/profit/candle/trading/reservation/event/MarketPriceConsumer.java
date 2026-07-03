package org.profit.candle.trading.reservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Market 도메인의 현재가 Kafka 이벤트를 구독해 OPEN+MARKET 예약을 즉시 체결한다.
 *
 * <p>토픽명({@code TOPIC})과 그룹 ID({@code GROUP_ID})는 Market 담당자(팀장)와 협의 후
 * 확정 예정 — 상수 교체만으로 반영 가능하도록 분리했다.</p>
 *
 * <p>이벤트 수신 시 당일 scheduled_date의 OPEN+MARKET RESERVED 예약 전체를 체결한다.
 * 같은 이벤트가 중복 수신돼도 이미 RESERVED가 아닌 예약은 skip하므로 멱등하다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceConsumer {

    // TODO: Market 담당자(팀장)와 협의 후 확정
    private static final String TOPIC = "market.open-price.v1";
    private static final String GROUP_ID = "trading-service-open-market";

    private final ReservationBatchService reservationBatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, String> record) {
        try {
            MarketPriceEvent event = objectMapper.readValue(record.value(), MarketPriceEvent.class);
            log.info("현재가 이벤트 수신 — symbol={}, price={}", event.symbol(), event.price());

            // 당일 OPEN+MARKET RESERVED 예약을 수신한 현재가로 체결
            int count = reservationBatchService.processOpenMarketReservations(
                    LocalDate.now(), event.symbol(), event.price());

            log.info("OPEN+MARKET 체결 완료 — symbol={}, price={}, count={}",
                    event.symbol(), event.price(), count);
        } catch (Exception e) {
            log.error("현재가 이벤트 처리 실패 — offset={}, value={}",
                    record.offset(), record.value(), e);
        }
    }
}