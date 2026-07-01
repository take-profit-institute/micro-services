CREATE TABLE portfolio_realized_trades (
    id              BIGSERIAL    NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    quantity        BIGINT       NOT NULL,
    entry_price     BIGINT       NOT NULL,
    exit_price      BIGINT       NOT NULL,
    realized_profit BIGINT       NOT NULL,
    opened_at       TIMESTAMP WITH TIME ZONE,
    closed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_portfolio_realized_trades_user_closed
    ON portfolio_realized_trades (user_id, closed_at);
