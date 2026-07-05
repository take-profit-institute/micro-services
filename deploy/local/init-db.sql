-- 로컬 postgres 초기화: 운영의 "단일 인스턴스 + 단일 DB + 서비스별 schema"를 동일하게 재현.
-- market은 별도 timescaledb 컨테이너(db=market)라 여기 없음.
-- 로컬 공용 접속 계정 (application.yml 기본값 / .env.development.example 과 동일)
-- POSTGRES_USER=candle 로 부트스트랩되면 initdb가 이미 candle 롤을 만들어 둔다.
-- 그 경우 CREATE USER가 에러 → ON_ERROR_STOP 으로 스크립트 전체가 중단되어
-- 아래 CREATE DATABASE/SCHEMA 들이 실행되지 않으므로, 멱등하게 가드한다.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'candle') THEN
    CREATE ROLE candle WITH LOGIN PASSWORD 'candle' SUPERUSER;
  END IF;
END
$$;

SELECT 'CREATE DATABASE candle WITH OWNER candle'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'candle')\gexec

\connect candle

CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS users AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS trading AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS account AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS order_svc AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS reservation AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS portfolio AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS ranking AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS mission AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS learning AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS batch AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS stock AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS wishlist AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS news AUTHORIZATION candle;
CREATE SCHEMA IF NOT EXISTS notification AUTHORIZATION candle;

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
