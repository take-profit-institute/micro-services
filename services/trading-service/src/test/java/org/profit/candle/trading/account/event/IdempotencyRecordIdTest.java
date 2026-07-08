package org.profit.candle.trading.account.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdempotencyRecordId(account 도메인)의 손으로 작성된 equals()/hashCode() 분기를 검증한다.
 * order.event.IdempotencyRecordIdTest와 동일한 구조 — 도메인별로 동일한 클래스가
 * 패키지만 다르게 복제돼 있다(컨벤션: per-domain duplication).
 */
class IdempotencyRecordIdTest {

    @Test
    @DisplayName("동일 인스턴스는 reflexive하게 true다")
    void shouldBeEqualToItself() {
        IdempotencyRecordId id = new IdempotencyRecordId("actor-1", "AccountService/GetBalance", "key-1");

        assertThat(id).isEqualTo(id);
    }

    @Test
    @DisplayName("null과 비교하면 false다")
    void shouldNotBeEqualToNull() {
        IdempotencyRecordId id = new IdempotencyRecordId("actor-1", "op", "key-1");

        assertThat(id).isNotEqualTo(null);
    }

    @Test
    @DisplayName("다른 타입과 비교하면 false다")
    void shouldNotBeEqualToDifferentType() {
        IdempotencyRecordId id = new IdempotencyRecordId("actor-1", "op", "key-1");

        assertThat(id).isNotEqualTo("actor-1");
    }

    @Test
    @DisplayName("actorId만 다르면 false다")
    void shouldNotBeEqualWhenActorIdDiffers() {
        IdempotencyRecordId a = new IdempotencyRecordId("actor-1", "op", "key-1");
        IdempotencyRecordId b = new IdempotencyRecordId("actor-2", "op", "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("operation만 다르면 false다")
    void shouldNotBeEqualWhenOperationDiffers() {
        IdempotencyRecordId a = new IdempotencyRecordId("actor-1", "op-a", "key-1");
        IdempotencyRecordId b = new IdempotencyRecordId("actor-1", "op-b", "key-1");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("idempotencyKey만 다르면 false다")
    void shouldNotBeEqualWhenIdempotencyKeyDiffers() {
        IdempotencyRecordId a = new IdempotencyRecordId("actor-1", "op", "key-1");
        IdempotencyRecordId b = new IdempotencyRecordId("actor-1", "op", "key-2");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("세 필드가 모두 같으면 true고 hashCode도 같다")
    void shouldBeEqualAndHaveSameHashCodeWhenAllFieldsMatch() {
        IdempotencyRecordId a = new IdempotencyRecordId("actor-1", "op", "key-1");
        IdempotencyRecordId b = new IdempotencyRecordId("actor-1", "op", "key-1");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("접근자(actorId/operation/idempotencyKey)가 생성자 값을 그대로 반환한다")
    void shouldExposeConstructorValuesViaAccessors() {
        IdempotencyRecordId id = new IdempotencyRecordId("actor-1", "AccountService/GetBalance", "key-1");

        assertThat(id.actorId()).isEqualTo("actor-1");
        assertThat(id.operation()).isEqualTo("AccountService/GetBalance");
        assertThat(id.idempotencyKey()).isEqualTo("key-1");
    }
}