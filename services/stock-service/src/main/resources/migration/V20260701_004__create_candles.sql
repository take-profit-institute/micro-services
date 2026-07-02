CREATE TABLE IF NOT EXISTS candles (
    stock_code varchar(6) NOT NULL,
    interval varchar(4) NOT NULL,
    open_time timestamptz NOT NULL,
    open bigint NOT NULL,
    high bigint NOT NULL,
    low bigint NOT NULL,
    close bigint NOT NULL,
    volume bigint NOT NULL,
    closed boolean NOT NULL DEFAULT true,
    source varchar(20) NOT NULL DEFAULT 'KIWOOM',
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (stock_code, interval, open_time)
);

CREATE INDEX IF NOT EXISTS idx_candles_lookup
    ON candles (stock_code, interval, open_time DESC);
