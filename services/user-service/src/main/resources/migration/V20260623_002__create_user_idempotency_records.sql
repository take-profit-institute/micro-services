CREATE TABLE user_idempotency_records (
  actor_id         VARCHAR(36)  NOT NULL,
  operation        VARCHAR(200) NOT NULL,
  idempotency_key  VARCHAR(64)  NOT NULL,
  request_hash     VARCHAR(64)  NOT NULL,
  response_payload BYTEA        NOT NULL,
  response_type    VARCHAR(200) NOT NULL,
  grpc_code        VARCHAR(40)  NOT NULL DEFAULT 'OK',
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at       TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (actor_id, operation, idempotency_key)
);

CREATE INDEX idx_user_idempotency_expires_at
  ON user_idempotency_records (expires_at);
