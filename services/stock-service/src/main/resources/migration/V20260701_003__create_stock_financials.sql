-- 종목 재무지표(분기 스냅샷). 카탈로그(stocks)와 분리 — 성격/생명주기가 다르고 이력 보관.
CREATE TABLE IF NOT EXISTS stock_financials (
    stock_id         bigint       NOT NULL REFERENCES stocks (stock_id) ON DELETE CASCADE,
    fiscal_period    varchar(7)   NOT NULL,           -- 예: '2025Q1'
    revenue          bigint,                          -- 매출액
    operating_profit bigint,                          -- 영업이익
    net_income       bigint,                          -- 순이익
    per              numeric(10, 2),
    pbr              numeric(10, 2),
    roe              numeric(6, 2),                    -- %
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (stock_id, fiscal_period)
);

COMMENT ON TABLE stock_financials IS '종목 재무지표(분기 스냅샷)';
COMMENT ON COLUMN stock_financials.fiscal_period IS '회계기간(YYYYQn)';

CREATE INDEX IF NOT EXISTS idx_stock_financials_stock ON stock_financials (stock_id);

DROP TRIGGER IF EXISTS trg_stock_financials_updated_at ON stock_financials;
CREATE TRIGGER trg_stock_financials_updated_at
    BEFORE UPDATE ON stock_financials
    FOR EACH ROW
    EXECUTE FUNCTION set_stocks_updated_at();   -- V001에서 만든 공용 트리거 함수 재사용
