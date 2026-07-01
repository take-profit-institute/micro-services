CREATE TABLE notification.idempotency_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_json JSONB NOT NULL,
    response_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_records_operation_key
        UNIQUE (user_id, operation, idempotency_key)
);

CREATE INDEX idx_idempotency_records_created
    ON notification.idempotency_records (created_at);
