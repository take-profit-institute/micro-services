package org.profit.candle.trading.reservation.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReservationIdempotencyRecordId — Lombok @EqualsAndHashCode가 생성한 equals()/hashCode()
 * 분기를 검증한다. Order 도메인의 손으로 작성한 버전과 의미상 동일한 계약이라
 * 테스트 구조도 동일하게 맞춘다.
 */
class ReservationIdempotencyRecordIdTest {

    @Test
    @DisplayName("동일 인스턴스는 reflexive하게 true다")
    void shouldBeEqualToItself() {
        ReservationIdempotencyRecordId id =
                new ReservationIdempotencyRecordId("actor-1", "ReservationService/PlaceReservation", "key-1");

        assertThat(id).isEqualTo(id);
    }

    @Test
    @DisplayName("null과 비교하면 false다")
    void shouldNotBeEqualToNull() {
        ReservationIdempotencyRecordId id =
                new ReservationIdempotencyRecordId("actor-1", "op", "key-1");

        assertThat(id).isNotEqualTo(null);
    }

    @Test
    @DisplayName("다른 타입과 비교하면 false다")
    void shouldNotBeEqualToDifferentType() {
        ReservationIdempotencyRecordId id =
                new ReservationIdempotencyRecordId("actor-1", "op", "key-1");

        assertThat(id).isNotEqualTo("actor-1");
    }

    @Test
    @DisplayName("actorId만 다르면 false다")
    void shouldNotBeEqualWhenActorIdDiffers() {
        ReservationIdempotencyRecordId a = new ReservationIdempotencyRecordId("actor-1", "op", "key-1");
        ReservationIdempotencyRecordId b = new ReservationIdempotencyRecordId("actor-2", "op", "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("operation만 다르면 false다")
    void shouldNotBeEqualWhenOperationDiffers() {
        ReservationIdempotencyRecordId a = new ReservationIdempotencyRecordId("actor-1", "op-a", "key-1");
        ReservationIdempotencyRecordId b = new ReservationIdempotencyRecordId("actor-1", "op-b", "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("idempotencyKey만 다르면 false다")
    void shouldNotBeEqualWhenIdempotencyKeyDiffers() {
        ReservationIdempotencyRecordId a = new ReservationIdempotencyRecordId("actor-1", "op", "key-1");
        ReservationIdempotencyRecordId b = new ReservationIdempotencyRecordId("actor-1", "op", "key-2");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("세 필드가 모두 같으면 true고 hashCode도 같다")
    void shouldBeEqualAndHaveSameHashCodeWhenAllFieldsMatch() {
        ReservationIdempotencyRecordId a = new ReservationIdempotencyRecordId("actor-1", "op", "key-1");
        ReservationIdempotencyRecordId b = new ReservationIdempotencyRecordId("actor-1", "op", "key-1");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("접근자가 생성자 값을 그대로 반환한다")
    void shouldExposeConstructorValuesViaAccessors() {
        ReservationIdempotencyRecordId id =
                new ReservationIdempotencyRecordId("actor-1", "ReservationService/PlaceReservation", "key-1");

        assertThat(id.actorId()).isEqualTo("actor-1");
        assertThat(id.operation()).isEqualTo("ReservationService/PlaceReservation");
        assertThat(id.idempotencyKey()).isEqualTo("key-1");
    }
}