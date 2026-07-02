CREATE TABLE ranking_idempotency_records (
    actor_id         VARCHAR(120) NOT NULL,
    operation        VARCHAR(200) NOT NULL,
    idempotency_key  VARCHAR(64)  NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    response_payload BYTEA        NOT NULL,
    response_type    VARCHAR(200) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (actor_id, operation, idempotency_key)
);

CREATE INDEX idx_ranking_idempotency_expires_at
    ON ranking_idempotency_records (expires_at);

CREATE TABLE ranking_outbox_events (
    event_id      UUID         NOT NULL,
    event_type    VARCHAR(120) NOT NULL,
    aggregate_id  VARCHAR(120) NOT NULL,
    payload       TEXT         NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_ranking_outbox_pending
    ON ranking_outbox_events (occurred_at)
    WHERE published_at IS NULL;
