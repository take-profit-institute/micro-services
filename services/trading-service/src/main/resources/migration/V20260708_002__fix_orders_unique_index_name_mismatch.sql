-- V20260708_001이 잘못된 인덱스 이름(idx_orders_unique_pending)을 DROP 대상으로 지정했다.
-- 그런 이름의 인덱스는 애초에 존재한 적이 없어(IF EXISTS라 에러 없이 no-op) 실제 옛날 인덱스인
-- uq_orders_account_symbol_pending(account_id, symbol)이 지워지지 않고 그대로 남아 있었다.
-- 그 결과 반대 side 주문(BUY PENDING이 있는 상태에서 SELL 등)이 애플리케이션 레벨 체크는
-- 통과하고도 INSERT 시점에 옛 유니크 제약에 걸려 여전히 막혔다.
--
-- reservation.reservations 쪽은 V20260701_001에서 만든 이름과 V20260708_001의 DROP 대상 이름이
-- 정확히 일치해(uq_reservations_account_symbol_reserved) 정상적으로 교체됐다 — 영향 없음.

-- 1) 진짜 옛날 인덱스를 이제라도 제거한다.
DROP INDEX IF EXISTS order_svc.uq_orders_account_symbol_pending;

-- 2) V20260708_001이 실수로 만든 idx_orders_unique_pending은 컨벤션(uq_ prefix)에도 안 맞고
--    이름만 다를 뿐 목적이 같으므로, 정리 차원에서 지우고 정식 이름으로 재생성한다.
DROP INDEX IF EXISTS order_svc.idx_orders_unique_pending;

CREATE UNIQUE INDEX uq_orders_account_symbol_side_pending
    ON order_svc.orders (account_id, symbol, side)
    WHERE status = 'PENDING';