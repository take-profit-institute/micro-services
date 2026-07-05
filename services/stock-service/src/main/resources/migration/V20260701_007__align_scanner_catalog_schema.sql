-- Align stock-service catalog schema with stock-items-scanner output.
-- The scanner keeps enriched Kiwoom ka10001 fields on stocks and normalizes
-- sector names through stock_sectors while retaining stocks.sector for
-- transition compatibility.

CREATE TABLE IF NOT EXISTS stock_sectors (
    sector_id bigserial PRIMARY KEY,
    sector_name varchar(100) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE stocks
    ADD COLUMN IF NOT EXISTS sector_id bigint,
    ADD COLUMN IF NOT EXISTS close_price bigint,
    ADD COLUMN IF NOT EXISTS change_rate numeric(8, 2),
    ADD COLUMN IF NOT EXISTS revenue bigint,
    ADD COLUMN IF NOT EXISTS operating_profit bigint,
    ADD COLUMN IF NOT EXISTS net_income bigint,
    ADD COLUMN IF NOT EXISTS per numeric(12, 2),
    ADD COLUMN IF NOT EXISTS eps numeric(18, 2),
    ADD COLUMN IF NOT EXISTS roe numeric(12, 2),
    ADD COLUMN IF NOT EXISTS pbr numeric(12, 2);

INSERT INTO stock_sectors (sector_name)
SELECT DISTINCT btrim(sector)
FROM stocks
WHERE sector IS NOT NULL
  AND btrim(sector) <> ''
ON CONFLICT (sector_name) DO NOTHING;

UPDATE stocks AS s
SET sector_id = ss.sector_id
FROM stock_sectors AS ss
WHERE s.sector_id IS NULL
  AND s.sector IS NOT NULL
  AND btrim(s.sector) = ss.sector_name;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'stocks'::regclass
          AND conname = 'stocks_sector_id_fkey'
    ) THEN
        ALTER TABLE stocks
            ADD CONSTRAINT stocks_sector_id_fkey
            FOREIGN KEY (sector_id) REFERENCES stock_sectors (sector_id);
    END IF;
END $$;

COMMENT ON TABLE stock_sectors IS '업종 마스터';
COMMENT ON COLUMN stock_sectors.sector_id IS '업종 ID';
COMMENT ON COLUMN stock_sectors.sector_name IS '업종명';
COMMENT ON COLUMN stock_sectors.created_at IS '생성일시';
COMMENT ON COLUMN stock_sectors.updated_at IS '수정일시';
COMMENT ON COLUMN stocks.sector_id IS '업종 ID';
COMMENT ON COLUMN stocks.sector IS '업종명(전환 호환용)';
COMMENT ON COLUMN stocks.close_price IS '현재가/종가(원, 키움 cur_prc)';
COMMENT ON COLUMN stocks.change_rate IS '등락률(%, 키움 flu_rt)';
COMMENT ON COLUMN stocks.revenue IS '매출액(억원, 키움 sale_amt)';
COMMENT ON COLUMN stocks.operating_profit IS '영업이익(억원, 키움 bus_pro)';
COMMENT ON COLUMN stocks.net_income IS '당기순이익(억원, 키움 cup_nga)';
COMMENT ON COLUMN stocks.per IS 'PER(키움 per)';
COMMENT ON COLUMN stocks.eps IS 'EPS(키움 eps)';
COMMENT ON COLUMN stocks.roe IS 'ROE(%, 키움 roe)';
COMMENT ON COLUMN stocks.pbr IS 'PBR(키움 pbr)';

CREATE INDEX IF NOT EXISTS idx_stocks_sector_id ON stocks (sector_id);

CREATE OR REPLACE FUNCTION set_stock_sectors_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_stock_sectors_updated_at ON stock_sectors;

CREATE TRIGGER trg_stock_sectors_updated_at
BEFORE UPDATE ON stock_sectors
FOR EACH ROW
EXECUTE FUNCTION set_stock_sectors_updated_at();
