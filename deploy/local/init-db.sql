-- 로컬 postgres 초기화: 운영의 "단일 인스턴스 + 서비스별 DB"를 동일하게 재현.
-- market은 별도 timescaledb 컨테이너(db=market)라 여기 없음.
-- 로컬 공용 접속 계정 (application.yml 기본값 / .env.development.example 과 동일)
CREATE USER candle WITH PASSWORD 'candle' SUPERUSER;

CREATE DATABASE candle_auth       WITH OWNER candle;
CREATE DATABASE candle_users      WITH OWNER candle;
CREATE DATABASE candle_trading    WITH OWNER candle;  -- account + trading 통합
CREATE DATABASE candle_portfolio  WITH OWNER candle;
CREATE DATABASE candle_ranking    WITH OWNER candle;
CREATE DATABASE candle_mission    WITH OWNER candle;
CREATE DATABASE candle_learning   WITH OWNER candle;
CREATE DATABASE candle_batch      WITH OWNER candle;  -- Spring Batch JobRepository
