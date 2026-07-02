-- V20260629_001에서 도입한 reservation 도메인 산출물을 proto/order_svc 컨벤션에 맞춰 정정한다.

-- 1) ENUM 값을 trading_common.proto의 ReservationTiming / OrderKind 명명과 맞춘다.
--    (proto: RESERVATION_TIMING_OPEN/TODAY_CLOSE/PREV_CLOSE, ORDER_KIND_MARKET/LIMIT/AFTER_HOURS_CLOSE)
--    CLOSE → TODAY_CLOSE, EXT_CLOSE → AFTER_HOURS_CLOSE로 값 이름만 변경한다.
--    컬럼 데이터 자체는 영향받지 않음 (값의 명칭만 변경, ALTER TYPE ... RENAME VALUE는 PostgreSQL 10+에서 지원).
ALTER TYPE reservation.reservation_timing RENAME VALUE 'CLOSE' TO 'TODAY_CLOSE';
ALTER TYPE reservation.order_kind RENAME VALUE 'EXT_CLOSE' TO 'AFTER_HOURS_CLOSE';

-- 2) CAN-006/007/008: 예약 정정은 원예약 취소 + parent 참조를 가진 신규 예약 생성 방식이다.
--    order_svc.orders의 parent_order_id와 동일한 패턴을 reservation에도 적용한다.
ALTER TABLE reservation.reservations
    ADD COLUMN parent_reservation_id UUID REFERENCES reservation.reservations (id) NULL;

COMMENT ON COLUMN reservation.reservations.parent_reservation_id IS
    '정정 시 원래 예약 ID (CAN-006/007/008). 동일 reservation 스키마 내부 참조이므로 FK 허용.';

-- 3) order_svc.orders.reserved_amount_krw와 동일 패턴: 취소 시 재계산하지 않고 생성 시점 값을 그대로 반환한다.
--    (재계산 방식은 수수료 정책 변경 시 선점 금액과 반환 금액이 어긋날 정합성 리스크가 있어 배제)
ALTER TABLE reservation.reservations
    ADD COLUMN reserved_amount_krw BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN reservation.reservations.reserved_amount_krw IS
    '이 예약이 잠근 금액(수수료 포함). BUY+LIMIT(시가+지정가)만 0보다 큼, 그 외(SELL/MARKET/AFTER_HOURS_CLOSE)는 0.';

-- 4) 컨벤션 13장(페이징)/14장(인덱스): ListReservations cursor 페이징이 타는 복합 인덱스.
--    기존 idx_reservations_user_id(user_id 단일)는 status 필터 + created_at/id 정렬을
--    커버하지 못해 cursor 쿼리(findPageAfterCursor/findFirstPage)가 풀스캔에 가까워진다.
--    이 테이블은 아직 운영 데이터가 없는 신규 테이블이라 인덱스 생성을 별도 migration으로
--    나눌 필요가 없다 (15장의 분리 규칙은 대용량 운영 테이블의 락 영향을 대상으로 한다).
--    동등 조건(user_id, status)을 앞에, 정렬 키(created_at DESC, id DESC)를 뒤에 둔다 (14장).
CREATE INDEX idx_reservations_user_status_created_id
    ON reservation.reservations (user_id, status, created_at DESC, id DESC);