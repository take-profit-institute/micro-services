CREATE TABLE IF NOT EXISTS outbox_events (
  id UUID PRIMARY KEY,
  event_type VARCHAR(120) NOT NULL,
  aggregate_id VARCHAR(120) NOT NULL,
  payload TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
  ON outbox_events (occurred_at) WHERE published_at IS NULL;
