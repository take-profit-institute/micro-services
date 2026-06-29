CREATE TABLE user_outbox_events (
    id          UUID         NOT NULL,
    topic       VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    payload     TEXT         NOT NULL,
    published   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_user_outbox_events_unpublished
    ON user_outbox_events (created_at ASC)
    WHERE published = FALSE;
