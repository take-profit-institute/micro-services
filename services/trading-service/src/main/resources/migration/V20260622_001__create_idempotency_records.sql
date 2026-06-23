-- 멱등성 레코드 (쓰기 gRPC 멱등성 키 설계 §4)
CREATE TABLE idempotency_records (
  actor_id         VARCHAR(120) NOT NULL,
  operation        VARCHAR(200) NOT NULL,
  idempotency_key  VARCHAR(64) NOT NULL,
  request_hash     VARCHAR(64) NOT NULL,
  response_payload BYTEA NOT NULL,
  response_type    VARCHAR(200) NOT NULL,
  grpc_code        VARCHAR(40) NOT NULL DEFAULT 'OK',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at       TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (actor_id, operation, idempotency_key)
);

-- 만료 정리 배치용 인덱스
CREATE INDEX idx_idempotency_records_expires_at
  ON idempotency_records (expires_at);
