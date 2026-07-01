CREATE TABLE wishlist_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    display_name VARCHAR(100),
    market VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_wishlist_items_user_symbol_active
    ON wishlist_items(user_id, symbol)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_wishlist_items_symbol_active
    ON wishlist_items(symbol)
    WHERE deleted_at IS NULL;

CREATE TABLE market_open_snapshots (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    trading_date DATE NOT NULL,
    open_price BIGINT NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_price BIGINT,
    last_change_basis_points INTEGER,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_market_open_snapshots_symbol_date UNIQUE(symbol, trading_date),
    CONSTRAINT chk_market_open_snapshots_open_price_positive CHECK (open_price > 0),
    CONSTRAINT chk_market_open_snapshots_last_price_positive CHECK (last_price IS NULL OR last_price > 0)
);

CREATE INDEX idx_market_open_snapshots_date
    ON market_open_snapshots(trading_date);

CREATE TABLE wishlist_price_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    trading_date DATE NOT NULL,
    direction VARCHAR(10) NOT NULL,
    threshold_basis_points INTEGER NOT NULL,
    open_price BIGINT NOT NULL,
    trigger_price BIGINT NOT NULL,
    change_basis_points INTEGER NOT NULL,
    notification_id UUID,
    idempotency_key VARCHAR(128) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_wishlist_price_alerts_direction CHECK (direction IN ('RISE', 'FALL')),
    CONSTRAINT chk_wishlist_price_alerts_threshold_positive CHECK (threshold_basis_points > 0),
    CONSTRAINT uk_wishlist_price_alerts_once UNIQUE(user_id, symbol, trading_date, direction, threshold_basis_points),
    CONSTRAINT uk_wishlist_price_alerts_idempotency UNIQUE(idempotency_key)
);

CREATE INDEX idx_wishlist_price_alerts_symbol_date
    ON wishlist_price_alerts(symbol, trading_date);

CREATE INDEX idx_wishlist_price_alerts_retry
    ON wishlist_price_alerts(created_at)
    WHERE notification_id IS NULL;
