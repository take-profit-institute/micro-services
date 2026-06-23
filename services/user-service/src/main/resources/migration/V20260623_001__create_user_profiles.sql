CREATE TABLE user_profiles (
  user_id           VARCHAR(36)  PRIMARY KEY,
  email             VARCHAR(320) NOT NULL,
  nickname          VARCHAR(50),
  profile_image_url VARCHAR(500),
  deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  version           BIGINT       NOT NULL DEFAULT 0,
  CONSTRAINT uk_user_profiles_email UNIQUE (email)
);

-- Kafka 이벤트 중복 소비 방지
CREATE TABLE consumed_events (
  event_id    UUID        PRIMARY KEY,
  event_type  VARCHAR(120) NOT NULL,
  consumed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
