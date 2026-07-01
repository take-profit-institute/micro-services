CREATE TABLE local_eod_accounts (
    user_id      VARCHAR(36) PRIMARY KEY,
    cash         BIGINT NOT NULL,
    seed_capital BIGINT NOT NULL CHECK (seed_capital > 0)
);

CREATE TABLE local_eod_holdings (
    user_id       VARCHAR(36) NOT NULL,
    symbol        VARCHAR(20) NOT NULL,
    quantity      BIGINT NOT NULL CHECK (quantity > 0),
    average_price BIGINT NOT NULL,
    closing_price BIGINT NOT NULL CHECK (closing_price > 0),
    active         BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, symbol),
    CONSTRAINT fk_local_eod_holding_account
        FOREIGN KEY (user_id) REFERENCES local_eod_accounts(user_id)
);

CREATE TABLE local_eod_snapshots (
    user_id                VARCHAR(36) NOT NULL,
    snapshot_date          DATE NOT NULL,
    total_asset            BIGINT NOT NULL,
    stock_value            BIGINT NOT NULL,
    daily_profit           BIGINT NOT NULL,
    cumulative_return_rate VARCHAR(20) NOT NULL,
    idempotency_key        VARCHAR(64) NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, snapshot_date)
);

INSERT INTO local_eod_accounts (user_id, cash, seed_capital) VALUES
    ('00000000-0000-0000-0000-000000000001', 100000, 500000),
    ('00000000-0000-0000-0000-000000000002', 200000, 400000);

INSERT INTO local_eod_holdings (
    user_id, symbol, quantity, average_price, closing_price, active
) VALUES
    ('00000000-0000-0000-0000-000000000001', '005930', 2, 60000, 70000, TRUE),
    ('00000000-0000-0000-0000-000000000001', '000660', 3, 100000, 110000, TRUE),
    ('00000000-0000-0000-0000-000000000002', '035420', 1, 180000, 200000, TRUE);

INSERT INTO local_eod_snapshots (
    user_id, snapshot_date, total_asset, stock_value,
    daily_profit, cumulative_return_rate, idempotency_key
) VALUES (
    '00000000-0000-0000-0000-000000000001', DATE '2026-06-29',
    550000, 450000, 0, '10.00', 'previous-snapshot'
);
