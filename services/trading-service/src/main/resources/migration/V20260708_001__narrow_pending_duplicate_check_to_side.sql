-- ORD-009 동일 종목 PENDING/RESERVED 중복 방지가 side(매수/매도)를 구분하지 않아
-- 반대 side 주문·예약까지 막던 문제 수정. 종목+side 조합으로 유일성 범위를 좁힌다.
--
-- 매도는 접수 시점에 보유 수량만 검증하고 현금을 잠그지 않으므로(reserved_balance 영향 없음),
-- 반대 side가 공존해도 잔고/수량 정합성에는 영향이 없다. 같은 side끼리 2개 이상 존재하는 것은
-- 여전히 막아야 하므로(초과매도 방지 등) 인덱스를 없애는 게 아니라 컬럼만 추가한다.

-- 1) order_svc.orders: (account_id, symbol) → (account_id, symbol, side)
DROP INDEX IF EXISTS order_svc.idx_orders_unique_pending;

CREATE UNIQUE INDEX idx_orders_unique_pending
    ON order_svc.orders (account_id, symbol, side)
    WHERE status = 'PENDING';

-- 2) reservation.reservations: (account_id, symbol) → (account_id, symbol, side)
DROP INDEX IF EXISTS reservation.uq_reservations_account_symbol_reserved;

CREATE UNIQUE INDEX uq_reservations_account_symbol_reserved
    ON reservation.reservations (account_id, symbol, side)
    WHERE status = 'RESERVED';