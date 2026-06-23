-- 로컬 postgres 초기화: 운영의 "단일 인스턴스 + 서비스별 DB"를 동일하게 재현.
-- market은 별도 timescaledb 컨테이너(db=market)라 여기 없음.
-- 로컬은 단순화를 위해 postgres 슈퍼유저로 접속(서비스별 role 미생성).
CREATE DATABASE candle_auth;
CREATE DATABASE candle_users;
CREATE DATABASE candle_trading;     -- account + trading 통합
CREATE DATABASE candle_portfolio;
CREATE DATABASE candle_ranking;
CREATE DATABASE candle_mission;
CREATE DATABASE candle_learning;
CREATE DATABASE candle_batch;       -- Spring Batch JobRepository
