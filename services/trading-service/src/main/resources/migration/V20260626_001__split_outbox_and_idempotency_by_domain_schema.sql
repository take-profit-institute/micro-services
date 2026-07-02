-- Account/Order 도메인별 outbox_events, idempotency_records 테이블 분리.
-- 기존 단일 outbox_events(스키마 미지정) 테이블은 이후 migration에서 정리한다.

CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS order_svc;

-- account 스키마
CREATE TABLE account.outbox_events (
                                       id           UUID PRIMARY KEY,
                                       event_type   VARCHAR(120) NOT NULL,
                                       aggregate_id VARCHAR(120) NOT NULL,
                                       payload      TEXT NOT NULL,
                                       occurred_at  TIMESTAMPTZ NOT NULL,
                                       published_at TIMESTAMPTZ
);

CREATE INDEX idx_account_outbox_events_pending
    ON account.outbox_events (occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE account.idempotency_records (
                                             actor_id         VARCHAR(120) NOT NULL,
                                             operation        VARCHAR(200) NOT NULL,
                                             idempotency_key  VARCHAR(64)  NOT NULL,
                                             request_hash     VARCHAR(64)  NOT NULL,
                                             response_payload BYTEA        NOT NULL,
                                             response_type    VARCHAR(200) NOT NULL,
                                             grpc_code        VARCHAR(40)  NOT NULL,
                                             created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                             expires_at       TIMESTAMPTZ  NOT NULL,
                                             PRIMARY KEY (actor_id, operation, idempotency_key)
);

-- order_svc 스키마
CREATE TABLE order_svc.outbox_events (
                                         id           UUID PRIMARY KEY,
                                         event_type   VARCHAR(120) NOT NULL,
                                         aggregate_id VARCHAR(120) NOT NULL,
                                         payload      TEXT NOT NULL,
                                         occurred_at  TIMESTAMPTZ NOT NULL,
                                         published_at TIMESTAMPTZ
);

CREATE INDEX idx_order_svc_outbox_events_pending
    ON order_svc.outbox_events (occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE order_svc.idempotency_records (
                                               actor_id         VARCHAR(120) NOT NULL,
                                               operation        VARCHAR(200) NOT NULL,
                                               idempotency_key  VARCHAR(64)  NOT NULL,
                                               request_hash     VARCHAR(64)  NOT NULL,
                                               response_payload BYTEA        NOT NULL,
                                               response_type    VARCHAR(200) NOT NULL,
                                               grpc_code        VARCHAR(40)  NOT NULL,
                                               created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                               expires_at       TIMESTAMPTZ  NOT NULL,
                                               PRIMARY KEY (actor_id, operation, idempotency_key)
);