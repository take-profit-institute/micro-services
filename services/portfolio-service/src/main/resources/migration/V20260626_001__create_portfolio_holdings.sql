CREATE TABLE portfolio_holdings (
    user_id              VARCHAR(36)  NOT NULL,
    symbol               VARCHAR(20)  NOT NULL,
    name                 VARCHAR(100) NOT NULL DEFAULT '',
    quantity             BIGINT       NOT NULL DEFAULT 0,
    average_price        BIGINT       NOT NULL DEFAULT 0,
    book_value           BIGINT       NOT NULL DEFAULT 0,
    cached_current_price BIGINT       NOT NULL DEFAULT 0,
    realized_profit      BIGINT       NOT NULL DEFAULT 0,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    sector               VARCHAR(100) NOT NULL DEFAULT '',
    market               VARCHAR(20)  NOT NULL DEFAULT '',
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, symbol)
);

CREATE INDEX idx_portfolio_holdings_user_active
    ON portfolio_holdings (user_id, active);
