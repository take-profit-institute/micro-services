-- 종목 마스터 확장: 검색 필터 / 상세 / 키움 fallback 신선도 추적용 컬럼.
-- 모두 nullable 또는 default 보유 → 기존 R__seed_stocks(코드/이름/시장만 삽입)와 호환.

ALTER TABLE stocks
    ADD COLUMN IF NOT EXISTS sector             varchar(50),
    ADD COLUMN IF NOT EXISTS listing_status     varchar(20)  NOT NULL DEFAULT 'LISTED',
    ADD COLUMN IF NOT EXISTS market_cap         bigint,
    ADD COLUMN IF NOT EXISTS shares_outstanding bigint,
    ADD COLUMN IF NOT EXISTS listed_at          date,
    ADD COLUMN IF NOT EXISTS data_source        varchar(20)  NOT NULL DEFAULT 'SEED',
    ADD COLUMN IF NOT EXISTS synced_at          timestamptz;

ALTER TABLE stocks
    ADD CONSTRAINT chk_stocks_listing_status
        CHECK (listing_status IN ('LISTED', 'DELISTED', 'SUSPENDED'));

COMMENT ON COLUMN stocks.sector IS '업종';
COMMENT ON COLUMN stocks.listing_status IS '상장상태(LISTED/DELISTED/SUSPENDED)';
COMMENT ON COLUMN stocks.market_cap IS '시가총액(배치 갱신)';
COMMENT ON COLUMN stocks.shares_outstanding IS '상장주식수';
COMMENT ON COLUMN stocks.listed_at IS '상장일';
COMMENT ON COLUMN stocks.data_source IS '데이터 출처(SEED/KIWOOM/BATCH)';
COMMENT ON COLUMN stocks.synced_at IS '마지막 키움 동기화 시각(신선도 판단)';

-- 필터 인덱스
CREATE INDEX IF NOT EXISTS idx_stocks_sector         ON stocks (sector);
CREATE INDEX IF NOT EXISTS idx_stocks_listing_status ON stocks (listing_status);
CREATE INDEX IF NOT EXISTS idx_stocks_market_cap     ON stocks (market_cap DESC);

-- 종목명 부분검색(LIKE '%q%') 가속용 trigram GIN
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_stocks_name_trgm ON stocks USING gin (stock_name gin_trgm_ops);
