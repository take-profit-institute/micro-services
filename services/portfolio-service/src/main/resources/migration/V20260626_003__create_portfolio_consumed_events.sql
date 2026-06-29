CREATE TABLE portfolio_consumed_events (
    event_id    UUID         NOT NULL,
    event_type  VARCHAR(100) NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id)
);
