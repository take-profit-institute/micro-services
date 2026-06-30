-- order_svc 도메인 체결 테이블 신규 생성
-- 논리모델 2.5 기준. 전량 체결만 지원하므로 주문 1건당 체결 1건(1:1).

CREATE TABLE order_svc.executions (
                                      id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      order_id            UUID NOT NULL,
                                      executed_price_krw  BIGINT NOT NULL,
                                      executed_quantity   BIGINT NOT NULL,
                                      fee_krw             BIGINT NOT NULL,
                                      tax_krw             BIGINT NOT NULL DEFAULT 0,
                                      net_amount_krw      BIGINT NOT NULL,
                                      executed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

                                      CONSTRAINT uq_executions_order_id UNIQUE (order_id),
                                      CONSTRAINT fk_executions_order
                                          FOREIGN KEY (order_id) REFERENCES order_svc.orders (id),

                                      CONSTRAINT chk_executions_price_positive CHECK (executed_price_krw > 0),
                                      CONSTRAINT chk_executions_quantity_positive CHECK (executed_quantity > 0),
                                      CONSTRAINT chk_executions_fee_non_negative CHECK (fee_krw >= 0),
                                      CONSTRAINT chk_executions_tax_non_negative CHECK (tax_krw >= 0)
);

CREATE INDEX idx_executions_order_id ON order_svc.executions (order_id);