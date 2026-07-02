CREATE SCHEMA IF NOT EXISTS learning;

-- ENUM 타입
CREATE TYPE learning.content_level_type AS ENUM ('초급', '중급', '고급');

-- 학습 콘텐츠
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
                                   deleted_at      TIMESTAMPTZ,
                                   created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 사용자별 콘텐츠 학습 상태
CREATE TABLE learning.user_content_states (
                                              id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                              user_id         UUID        NOT NULL,
                                              content_id      UUID        NOT NULL REFERENCES learning.contents(id),
                                              progress_pct    SMALLINT    NOT NULL DEFAULT 0,
                                              is_completed    BOOLEAN     NOT NULL DEFAULT false,
                                              is_favorite     BOOLEAN     NOT NULL DEFAULT false,
                                              completed_at    TIMESTAMPTZ,
                                              last_read_at    TIMESTAMPTZ,
                                              created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                              updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

                                              CONSTRAINT uq_user_content_states_user_content UNIQUE (user_id, content_id),
                                              CONSTRAINT ck_user_content_states_progress CHECK (progress_pct BETWEEN 0 AND 100)
);
-- === 인덱스 ===
-- 콘텐츠 목록 조회: 공개 + 미삭제 필터 (거의 모든 조회에 사용)
CREATE INDEX idx_contents_published_alive
    ON learning.contents (is_published, created_at DESC)
    WHERE deleted_at IS NULL;

-- 카테고리 + 레벨 필터링
CREATE INDEX idx_contents_category_level
    ON learning.contents (category, level)
    WHERE deleted_at IS NULL AND is_published = true;

-- 인기순/조회순 정렬
CREATE INDEX idx_contents_read_count
    ON learning.contents (read_count DESC)
    WHERE deleted_at IS NULL AND is_published = true;

-- 키워드 검색 (GIN)
CREATE INDEX idx_contents_keywords
    ON learning.contents USING GIN (keywords)
    WHERE deleted_at IS NULL AND is_published = true;

-- 사용자별 학습 상태 조회
CREATE INDEX idx_user_content_states_user_id
    ON learning.user_content_states (user_id);

-- 즐겨찾기 목록 조회
CREATE INDEX idx_user_content_states_favorite
    ON learning.user_content_states (user_id)
    WHERE is_favorite = true;