CREATE TABLE notification.outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    trace_id TEXT,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished
    ON notification.outbox_events (occurred_at, event_id)
    WHERE published_at IS NULL;
