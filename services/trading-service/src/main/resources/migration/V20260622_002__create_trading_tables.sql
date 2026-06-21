-- 거래 도메인 테이블

CREATE TABLE account_balances (
  user_id          VARCHAR(120) PRIMARY KEY,
  cash             BIGINT NOT NULL DEFAULT 0,
  reserved_balance BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE orders (
  id              VARCHAR(40) PRIMARY KEY,
  user_id         VARCHAR(120) NOT NULL,
  symbol          VARCHAR(40) NOT NULL,
  side            VARCHAR(16) NOT NULL,
  kind            VARCHAR(24) NOT NULL,
  quantity        BIGINT NOT NULL,
  price           BIGINT NOT NULL,
  status          VARCHAR(16) NOT NULL,
  parent_order_id VARCHAR(40),
  reserved_amount BIGINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_status ON orders (user_id, status);

CREATE TABLE holdings (
  user_id         VARCHAR(120) NOT NULL,
  symbol          VARCHAR(40) NOT NULL,
  quantity        BIGINT NOT NULL DEFAULT 0,
  average_price   BIGINT NOT NULL DEFAULT 0,
  realized_profit BIGINT NOT NULL DEFAULT 0,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (user_id, symbol)
);

CREATE TABLE outbox_events (
  id           UUID PRIMARY KEY,
  event_type   VARCHAR(120) NOT NULL,
  aggregate_id VARCHAR(120) NOT NULL,
  payload      TEXT NOT NULL,
  occurred_at  TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at) WHERE published_at IS NULL;
