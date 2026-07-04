CREATE TABLE ranking_participants (
    user_id        UUID         NOT NULL,
    nickname       VARCHAR(100) NOT NULL,
    trade_count    INTEGER      NOT NULL DEFAULT 0,
    user_status    VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    account_status VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id),
    CHECK (trade_count >= 0),
    CHECK (user_status IN ('UNKNOWN', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')),
    CHECK (account_status IN ('UNKNOWN', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE ranking_consumed_events (
    source_service VARCHAR(50)  NOT NULL,
    event_id       UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    consumed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (source_service, event_id)
);
