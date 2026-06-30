-- Reservation 도메인 신규 스키마 생성.
-- account/order_svc와 동일 패턴: outbox_events, idempotency_records 분리.
-- reservations 테이블은 시가/당일종가/전일종가 예약 주문의 생명주기를 단독 관리하며,
-- 시가+지정가 케이스에서만 order_svc로 전환(CONVERTING)된다.

CREATE SCHEMA IF NOT EXISTS reservation;

-- reservation 스키마: outbox_events
CREATE TABLE reservation.outbox_events (
                                           id           UUID PRIMARY KEY,
                                           event_type   VARCHAR(120) NOT NULL,
                                           aggregate_id VARCHAR(120) NOT NULL,
                                           payload      TEXT NOT NULL,
                                           occurred_at  TIMESTAMPTZ NOT NULL,
                                           published_at TIMESTAMPTZ
);

CREATE INDEX idx_reservation_outbox_events_pending
    ON reservation.outbox_events (occurred_at)
    WHERE published_at IS NULL;

-- reservation 스키마: idempotency_records
CREATE TABLE reservation.idempotency_records (
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

-- reservation 스키마: reservation_timing, reservation_status ENUM
CREATE TYPE reservation.reservation_timing AS ENUM (
    'OPEN',        -- 시가
    'CLOSE',       -- 당일종가
    'PREV_CLOSE'   -- 전일종가
);

CREATE TYPE reservation.order_kind AS ENUM (
    'MARKET',
    'LIMIT',
    'EXT_CLOSE'    -- 시간외종가
);

CREATE TYPE reservation.reservation_status AS ENUM (
    'RESERVED',     -- 접수 완료, 배치 실행 대기
    'CONVERTING',   -- 시가+지정가 전환 진행 중 (order_svc로 ReservationDue 발행~ReservationConverted 수신 전)
    'EXECUTED',     -- 체결 완료 (자체 완결) 또는 전환 완료 (시가+지정가)
    'CANCELLED',    -- 사용자 요청 취소
    'FAILED',       -- 잔고 부족 등 처리 실패
    'EXPIRED'       -- 접수 마감 후 미처리 등 자동 만료
);

-- reservation 스키마: reservations
CREATE TABLE reservation.reservations (
                                          id                  UUID PRIMARY KEY,
                                          user_id             UUID NOT NULL,
                                          account_id          UUID NOT NULL,                 -- 크로스 스키마, 값만 복사 (FK 없음)
                                          symbol              VARCHAR(20) NOT NULL,           -- 크로스 스키마, 값만 복사 (FK 없음)
                                          side                VARCHAR(10) NOT NULL,           -- buy / sell
                                          timing              reservation.reservation_timing NOT NULL,
                                          order_kind          reservation.order_kind NOT NULL,
                                          quantity            BIGINT NOT NULL,
                                          price_krw           BIGINT,                         -- 시가+지정가일 때만 값 존재
                                          scheduled_date      DATE NOT NULL,                   -- 전일종가는 익일 고정, 나머지는 최대 7일 이내
                                          status              reservation.reservation_status NOT NULL DEFAULT 'RESERVED',
                                          converted_order_id  UUID,                            -- 크로스 스키마, 값만 복사 — 시가+지정가 전환 시에만 채워짐
                                          idempotency_key     TEXT NOT NULL,
                                          expires_at          TIMESTAMPTZ,
                                          created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                          updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

                                          CONSTRAINT uq_reservations_idempotency_key UNIQUE (idempotency_key),

    -- side 제약: buy / sell만 허용
                                          CONSTRAINT chk_reservations_side CHECK (side IN ('buy', 'sell')),

    -- timing별 order_kind 제약 (RSV-002, RSV-003)
                                          CONSTRAINT chk_reservations_timing_order_kind CHECK (
                                              (timing = 'OPEN' AND order_kind IN ('MARKET', 'LIMIT'))
                                                  OR (timing IN ('CLOSE', 'PREV_CLOSE') AND order_kind = 'EXT_CLOSE')
                                              ),

    -- 지정가일 때만 price_krw 존재 (시가+지정가 케이스만 price_krw NOT NULL)
                                          CONSTRAINT chk_reservations_price_krw CHECK (
                                              (order_kind = 'LIMIT' AND price_krw IS NOT NULL)
                                                  OR (order_kind != 'LIMIT' AND price_krw IS NULL)
                                              )
);

-- 사용자별 예약 목록 조회 (RSV-009) 가속
CREATE INDEX idx_reservations_user_id
    ON reservation.reservations (user_id);

-- 배치 처리 대상 조회 가속: scheduled_date + status + timing 조합으로 "오늘 처리할 RESERVED 건" 조회
CREATE INDEX idx_reservations_batch_lookup
    ON reservation.reservations (scheduled_date, status, timing)
    WHERE status = 'RESERVED';