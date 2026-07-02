CREATE TABLE portfolio_snapshots (
    id                     BIGSERIAL    NOT NULL,
    user_id                VARCHAR(36)  NOT NULL,
    snapshot_date          DATE         NOT NULL,
    total_asset            BIGINT       NOT NULL DEFAULT 0,
    stock_value            BIGINT       NOT NULL DEFAULT 0,
    daily_profit           BIGINT       NOT NULL DEFAULT 0,
    cumulative_return_rate VARCHAR(20)  NOT NULL DEFAULT '0.00',
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, snapshot_date)
);

CREATE INDEX idx_portfolio_snapshots_user_date
    ON portfolio_snapshots (user_id, snapshot_date DESC);
