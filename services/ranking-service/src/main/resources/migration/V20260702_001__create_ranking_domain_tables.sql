CREATE TABLE ranking_runs (
    ranking_date      DATE        NOT NULL,
    ranked_user_count INTEGER     NOT NULL,
    completed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (ranking_date),
    CHECK (ranked_user_count >= 0)
);

CREATE TABLE ranking_snapshot (
    snapshot_id   BIGSERIAL     NOT NULL,
    user_id       UUID          NOT NULL,
    total_asset   BIGINT        NOT NULL,
    profit_rate   NUMERIC(10,4) NOT NULL,
    trade_count   INTEGER       NOT NULL,
    snapshot_date DATE          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (snapshot_id),
    UNIQUE (user_id, snapshot_date),
    CHECK (total_asset >= 0),
    CHECK (trade_count >= 0)
);

CREATE INDEX idx_ranking_snapshot_date_order
    ON ranking_snapshot (snapshot_date, profit_rate DESC, trade_count DESC, user_id ASC);

CREATE TABLE ranking_history (
    ranking_id       BIGSERIAL     NOT NULL,
    user_id          UUID          NOT NULL,
    ranking_position INTEGER       NOT NULL,
    total_asset      BIGINT        NOT NULL,
    profit_rate      NUMERIC(10,4) NOT NULL,
    trade_count      INTEGER       NOT NULL,
    ranking_date     DATE          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (ranking_id),
    UNIQUE (user_id, ranking_date),
    UNIQUE (ranking_date, ranking_position),
    CHECK (ranking_position > 0),
    CHECK (total_asset >= 0),
    CHECK (trade_count >= 5)
);
