-- 1) side CHECK 제약: 소문자 → 대문자로 수정 (JPA EnumType.STRING은 대문자로 저장)
ALTER TABLE reservation.reservations
DROP CONSTRAINT chk_reservations_side;
ALTER TABLE reservation.reservations
    ADD CONSTRAINT chk_reservations_side CHECK (side IN ('BUY', 'SELL'));

-- 2) timing/order_kind CHECK 제약: ENUM rename 이후 값 반영
ALTER TABLE reservation.reservations
DROP CONSTRAINT chk_reservations_timing_order_kind;
ALTER TABLE reservation.reservations
    ADD CONSTRAINT chk_reservations_timing_order_kind CHECK (
        (timing = 'OPEN' AND order_kind IN ('MARKET', 'LIMIT'))
            OR (timing IN ('TODAY_CLOSE', 'PREV_CLOSE') AND order_kind = 'AFTER_HOURS_CLOSE')
        );

-- 3) 동시 예약 중복 방지: (account_id, symbol) WHERE status='RESERVED' 부분 유니크 인덱스
CREATE UNIQUE INDEX uq_reservations_account_symbol_reserved
    ON reservation.reservations (account_id, symbol)
    WHERE status = 'RESERVED';