CREATE TABLE admin_accounts (
  id              UUID PRIMARY KEY,
  username        VARCHAR(50)  NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,
  display_name    VARCHAR(100) NOT NULL,
  role            VARCHAR(20)  NOT NULL,
  status          VARCHAR(20)  NOT NULL,
  failed_attempts INT          NOT NULL DEFAULT 0,
  locked_until    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ  NOT NULL,
  updated_at      TIMESTAMPTZ  NOT NULL,
  last_login_at   TIMESTAMPTZ,
  CONSTRAINT uk_admin_accounts_username UNIQUE (username)
);

-- refresh token 회전(rotate) 시 어느 principal(사용자/관리자)로 재발급할지 판별한다.
ALTER TABLE refresh_tokens
  ADD COLUMN principal_type VARCHAR(10) NOT NULL DEFAULT 'USER';
