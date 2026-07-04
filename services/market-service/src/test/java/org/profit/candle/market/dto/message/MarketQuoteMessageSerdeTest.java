package org.profit.candle.market.dto.message;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * market:quotes 발행 JSON 이 wishlist-service 의 QuoteTick 계약과 라운드트립하는지 검증한다.
 * 실제 계약이 깨지는 지점(타입정보 @class 삽입, java.time 직렬화 형식)을 고정한다.
 */
class MarketQuoteMessageSerdeTest {

    // wishlist-service QuoteTick 과 동일한 형태 (소비 측 계약 복제)
    private record QuoteTick(
            String symbol,
            long price,
            long openPrice,
            String marketStatus,
            LocalDate tradingDate,
            Instant timestamp
    ) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialized_json_isPlain_and_roundTripsIntoQuoteTick() {
        Instant ts = Instant.parse("2026-07-03T01:47:09Z");
        MarketQuoteMessage message = new MarketQuoteMessage(
                "005930", 299000L, 288500L, "OPEN", LocalDate.of(2026, 7, 3), ts);

        String json = objectMapper.writeValueAsString(message);

        // 소비 측은 body 를 그대로 파싱하므로 다형성 타입정보가 있으면 안 된다
        assertThat(json).doesNotContain("@class");
        // java.time 은 배열/타임스탬프가 아니라 ISO 문자열이어야 한다
        assertThat(json).contains("\"tradingDate\":\"2026-07-03\"");
        assertThat(json).contains("\"symbol\":\"005930\"");

        QuoteTick parsed = objectMapper.readValue(json, QuoteTick.class);

        assertThat(parsed.symbol()).isEqualTo("005930");
        assertThat(parsed.price()).isEqualTo(299000L);
        assertThat(parsed.openPrice()).isEqualTo(288500L);
        assertThat(parsed.marketStatus()).isEqualTo("OPEN");
        assertThat(parsed.tradingDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(parsed.timestamp()).isEqualTo(ts);
    }
}
