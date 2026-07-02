-- account 도메인 Kafka 컨슈머 멱등성 보장용 consumed_events 테이블.
-- user 서비스의 consumed_events와 동일 구조, account 스키마에 분리 생성.
-- event_id(UUID) PK로 같은 이벤트가 두 번 오면 existsById로 스킵한다.

CREATE TABLE account.consumed_events (
                                         event_id    UUID        NOT NULL,
                                         event_type  VARCHAR(120) NOT NULL,
                                         consumed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                                         PRIMARY KEY (event_id)
);