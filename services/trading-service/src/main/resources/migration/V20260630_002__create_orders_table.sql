-- order_svc 도메인 주문 테이블 신규 생성 (이슈 #44)
-- order_svc.outbox_events, order_svc.idempotency_records는 기존 스키마 분리
-- 마이그레이션(V20260626 계열)에서 이미 생성되어 있어 본 작업 범위에서 제외.

CREATE TYPE order_svc.order_side AS ENUM (
    'BUY',
    'SELL'
);

CREATE TYPE order_svc.order_kind AS ENUM (
    'MARKET',
    'LIMIT'
);

CREATE TYPE order_svc.order_status AS ENUM (
    'PENDING',
    'FILLED',
    'CANCELLED',
    'FAILED'
);

CREATE TABLE order_svc.orders (
                                  id                   UUID PRIMARY KEY,
                                  user_id              UUID NOT NULL,
                                  account_id           UUID NOT NULL,                 -- 크로스 스키마, 값만 복사 (FK 없음)
                                  symbol               VARCHAR(20) NOT NULL,           -- 크로스 스키마, 값만 복사 (FK 없음)
                                  side                 order_svc.order_side NOT NULL,
                                  order_kind           order_svc.order_kind NOT NULL,
                                  quantity             BIGINT NOT NULL,
                                  price_krw            BIGINT,                         -- LIMIT일 때만 값 존재
                                  reserved_amount_krw  BIGINT NOT NULL DEFAULT 0,       -- 이 주문이 잠근 금액 (수수료 포함)
                                  status               order_svc.order_status NOT NULL DEFAULT 'PENDING',
                                  parent_order_id      UUID,                            -- 정정 시 원 주문 참조 (self-FK)
                                  idempotency_key      TEXT NOT NULL,
                                  executed_at          TIMESTAMPTZ,
                                  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

                                  CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),

                                  CONSTRAINT fk_orders_parent_order
                                      FOREIGN KEY (parent_order_id) REFERENCES order_svc.orders (id),

    -- 수량/가격은 양수
                                  CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
                                  CONSTRAINT chk_orders_price_krw_positive CHECK (price_krw IS NULL OR price_krw > 0),

    -- LIMIT 주문은 price_krw 필수, MARKET 주문은 price_krw 없어야 함 (논리모델 2.4 제약)
                                  CONSTRAINT chk_orders_price_krw_by_kind CHECK (
                                      (order_kind = 'LIMIT' AND price_krw IS NOT NULL)
                                          OR (order_kind = 'MARKET' AND price_krw IS NULL)
                                      ),

                                  CONSTRAINT chk_orders_reserved_amount_non_negative CHECK (reserved_amount_krw >= 0)
);

-- 동일 계좌·동일 종목 PENDING 주문 1건만 허용 (ORD-009)
-- 부분 unique index: status가 PENDING인 행에만 (account_id, symbol) 유일성 강제
CREATE UNIQUE INDEX uq_orders_account_symbol_pending
    ON order_svc.orders (account_id, symbol)
    WHERE status = 'PENDING';

-- 사용자별 주문 목록 조회(ORD-004) 가속: 상태 필터 + 최신순 정렬
CREATE INDEX idx_orders_user_status_created_at
    ON order_svc.orders (user_id, status, created_at DESC, id DESC);