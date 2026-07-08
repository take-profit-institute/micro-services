package org.profit.candle.trading.support.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TradingOutboxTopics — eventType→토픽 매핑 규칙 검증.
 * package-private 클래스라 같은 패키지에 테스트를 둔다.
 * support.event 패키지에서 유일하게 실제 분기(OrderFilled 특례 처리)가 있는 지점.
 */
class TradingOutboxTopicsTest {

    @Nested
    @DisplayName("forOrderEvent")
    class ForOrderEvent {

        @Test
        @DisplayName("OrderFilled는 계약이 굳은 특수 토픽(orderFilled)으로 매핑된다")
        void shouldMapOrderFilledToLegacyContractTopic() {
            assertThat(TradingOutboxTopics.forOrderEvent("OrderFilled")).isEqualTo("orderFilled");
        }

        @Test
        @DisplayName("그 외 이벤트는 기본 규칙(trading.order.<EventType>)을 따른다")
        void shouldApplyDefaultRuleForOtherEventTypes() {
            assertThat(TradingOutboxTopics.forOrderEvent("OrderPlaced"))
                    .isEqualTo("trading.order.OrderPlaced");
            assertThat(TradingOutboxTopics.forOrderEvent("OrderCancelled"))
                    .isEqualTo("trading.order.OrderCancelled");
        }
    }

    @Nested
    @DisplayName("forReservationEvent / forAccountEvent")
    class OtherPrefixes {

        @Test
        void shouldApplyReservationPrefix() {
            assertThat(TradingOutboxTopics.forReservationEvent("ReservationDue"))
                    .isEqualTo("trading.reservation.ReservationDue");
        }

        @Test
        void shouldApplyAccountPrefix() {
            assertThat(TradingOutboxTopics.forAccountEvent("SomeAccountEvent"))
                    .isEqualTo("trading.account.SomeAccountEvent");
        }
    }

    @Test
    @DisplayName("유틸리티 클래스라 인스턴스화하면 AssertionError를 던진다")
    void shouldThrowWhenInstantiatedViaReflection() throws NoSuchMethodException {
        Constructor<TradingOutboxTopics> constructor = TradingOutboxTopics.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }
}