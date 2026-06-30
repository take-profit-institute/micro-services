-- account 도메인 계좌 마스터 테이블 신규 생성
-- (원래 이슈 #43에서 작성됐으나 마이그레이션 파일 반영이 누락되어 #44에서 함께 반영)
-- account.outbox_events, account.idempotency_records는 기존 스키마 분리
-- 마이그레이션(V20260626 계열)에서 이미 생성되어 있어 본 작업 범위에서 제외.
--
-- holdings(보유종목)는 본 서비스 소유 도메인이 아니므로 포함하지 않는다.
-- (Holding/Portfolio Service 소유)

CREATE TYPE account.account_status AS ENUM (
    'ACTIVE',
    'INACTIVE'
);

CREATE TABLE account.accounts (
                                  id          UUID PRIMARY KEY,
                                  user_id     UUID NOT NULL,                          -- Auth/User 서비스 소유, 값만 복사
                                  status      account.account_status NOT NULL DEFAULT 'ACTIVE',
                                  cash_krw    BIGINT NOT NULL DEFAULT 0,               -- 현금잔고
                                  locked_krw  BIGINT NOT NULL DEFAULT 0,               -- 잠금금액 (가용금액은 저장하지 않고 cash_krw - locked_krw로 계산)
                                  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

                                  CONSTRAINT uq_accounts_user_id UNIQUE (user_id),    -- 1인당 계좌 1개 제약

    -- 잔고는 음수가 될 수 없고, 잠금금액은 현금잔고를 초과할 수 없다
                                  CONSTRAINT chk_accounts_cash_non_negative CHECK (cash_krw >= 0),
                                  CONSTRAINT chk_accounts_locked_non_negative CHECK (locked_krw >= 0),
                                  CONSTRAINT chk_accounts_locked_not_exceed_cash CHECK (locked_krw <= cash_krw)
);

-- user_id로 계좌 조회 가속 (UNIQUE 제약이 이미 인덱스를 생성하지만 명시적으로 의도 표기)
-- uq_accounts_user_id가 자동으로 인덱스를 생성하므로 별도 인덱스 불필요