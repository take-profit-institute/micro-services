-- 로컬 postgres 초기화: 운영의 "단일 인스턴스 + 서비스별 DB"를 동일하게 재현.
-- market은 별도 timescaledb 컨테이너(db=market)라 여기 없음.
-- 로컬 공용 접속 계정 (application.yml 기본값 / .env.development.example 과 동일)
-- POSTGRES_USER=candle 로 부트스트랩되면 initdb가 이미 candle 롤을 만들어 둔다.
-- 그 경우 CREATE USER가 에러 → ON_ERROR_STOP 으로 스크립트 전체가 중단되어
-- 아래 CREATE DATABASE 들이 실행되지 않으므로, 멱등하게 가드한다.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'candle') THEN
    CREATE ROLE candle WITH LOGIN PASSWORD 'candle' SUPERUSER;
  END IF;
END
$$;

CREATE DATABASE candle_auth       WITH OWNER candle;
CREATE DATABASE candle_users      WITH OWNER candle;
CREATE DATABASE candle_stock      WITH OWNER candle;
CREATE DATABASE candle_market      WITH OWNER candle;
CREATE DATABASE candle_notification WITH OWNER candle;
CREATE DATABASE candle_wishlist    WITH OWNER candle;
CREATE DATABASE candle_news       WITH OWNER candle;

CREATE DATABASE candle_trading    WITH OWNER candle;  -- account + trading 통합
CREATE DATABASE candle_portfolio  WITH OWNER candle;
CREATE DATABASE candle_ranking    WITH OWNER candle;
CREATE DATABASE candle_mission    WITH OWNER candle;
CREATE DATABASE candle_learning   WITH OWNER candle;
CREATE DATABASE candle_batch      WITH OWNER candle;  -- Spring Batch JobRepository
