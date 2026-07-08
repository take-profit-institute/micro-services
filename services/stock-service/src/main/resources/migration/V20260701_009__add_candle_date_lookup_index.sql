CREATE INDEX IF NOT EXISTS idx_candles_interval_open_time_stock_code
    ON candles (interval, open_time, stock_code);
