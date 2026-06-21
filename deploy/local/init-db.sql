-- 로컬 postgres 초기화: 운영의 "단일 인스턴스 + 서비스별 DB"를 동일하게 재현.
-- market은 별도 timescaledb 컨테이너(db=market)라 여기 없음.
-- 로컬은 단순화를 위해 postgres 슈퍼유저로 접속(서비스별 role 미생성).
CREATE DATABASE auth;
CREATE DATABASE users;
CREATE DATABASE trading;     -- account + trading 통합
CREATE DATABASE portfolio;
CREATE DATABASE ranking;
CREATE DATABASE mission;
CREATE DATABASE learning;
CREATE DATABASE batch;       -- Spring Batch JobRepository
