CREATE SCHEMA IF NOT EXISTS learning;

-- 2. 혹시 기존에 만들다 실패한 타입이 있다면 삭제 (안전장치)
DROP TYPE IF EXISTS learning.content_level_type CASCADE;

-- 3. 타입 깔끔하게 새로 생성
CREATE TYPE learning.content_level_type AS ENUM ('초급', '중급', '고급');

CREATE TABLE learning.contents (
                                   id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                   title           TEXT        NOT NULL,
                                   description     TEXT,
                                   category        TEXT        NOT NULL,
                                   level           learning.content_level_type NOT NULL,
                                   body            TEXT,
                                   duration_min    SMALLINT    NOT NULL DEFAULT 0,
                                   xp_reward       BIGINT      NOT NULL DEFAULT 0,
                                   keywords        TEXT[]      NOT NULL DEFAULT '{}',
                                   is_published    BOOLEAN     NOT NULL DEFAULT false,
                                   read_count      BIGINT      NOT NULL DEFAULT 0,
                                   created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);



CREATE TABLE learning.user_content_states (
                                              id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                              user_id         UUID        NOT NULL,
                                              content_id      UUID        NOT NULL REFERENCES learning.contents(id),
                                              progress_pct    SMALLINT    NOT NULL DEFAULT 0,  -- 0~100
                                              is_completed    BOOLEAN     NOT NULL DEFAULT false,
                                              is_favorite     BOOLEAN     NOT NULL DEFAULT false,
                                              completed_at    TIMESTAMPTZ,
                                              last_read_at    TIMESTAMPTZ,
                                              created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                              updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

                                              CONSTRAINT uq_user_content_states_user_content UNIQUE (user_id, content_id),
                                              CONSTRAINT ck_user_content_states_progress
                                                  CHECK (progress_pct BETWEEN 0 AND 100)
);