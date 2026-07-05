# Ranking Service 개발 및 연동 가이드

## 1. 현재 구현 범위

Ranking Service는 Portfolio의 거래일별 EOD 스냅샷을 입력으로 일별 수익률 순위를 저장하고
마지막 완료 랭킹을 조회한다.

- `auth.user-created.v1`: 신규 참가자 등록
- `user.profile-updated.v1`: 닉네임 갱신
- `orderFilled`: 누적 거래 횟수 증가
- `(source_service, event_id)` 외부 이벤트 중복 방지
- 거래 5회 이상이며 사용자·계좌 상태가 모두 `ACTIVE`인 참가자만 포함
- 정렬: 수익률 DESC, 거래 횟수 DESC, 사용자 ID ASC
- Portfolio `ListDailyPortfolioSnapshots` 실제 gRPC 연동
- `FinalizeDailyRanking` 멱등성·Outbox·동일 transaction
- `ListRankings`, `GetMyRanking`, cursor pagination
- Redis cache-aside와 PostgreSQL fallback

현재 탈퇴·정지·계좌 비활성 기능은 후순위다. 팀 합의에 따라 가입한 사용자와 신규 계좌를
`ACTIVE`로 초기화하며, 기존 `UNKNOWN` 데이터도 Flyway migration으로 `ACTIVE` 전환한다.

## 2. 전체 흐름

```text
UserCreated/UserProfileUpdated ────────┐
                                       ├─→ ranking_participants
일반·예약 주문 OrderFilled ────────────┘       ├─ nickname
                                               ├─ trade_count
                                               └─ ACTIVE 상태

Batch → FinalizeDailyRanking
      → Portfolio ListDailyPortfolioSnapshots 전체 cursor 조회
      → 5회·ACTIVE 대상자 필터
      → 수익률/거래 횟수/userId 정렬
      → ranking_snapshot + ranking_history + ranking_runs
      → ranking_outbox_events + ranking_idempotency_records

조회 → Redis → miss/장애 시 PostgreSQL → Redis 복구
```

PostgreSQL이 원본이다. Redis 데이터는 삭제되거나 장애가 발생해도 DB에서 복구된다.

## 3. 현재 외부 계약

### Portfolio

- RPC: `candle.portfolio.v1.PortfolioService/ListDailyPortfolioSnapshots`
- 기본 주소: `static://localhost:50055`
- 입력: KST 거래일, page size 최대 500, cursor
- 출력: `user_id`, `total_asset`, `cumulative_return_rate`
- Ranking은 수익률을 다시 계산하지 않는다.

환경변수:

```text
RANKING_PORTFOLIO_GRPC_TARGET
RANKING_PORTFOLIO_GRPC_DEADLINE
```

### Trading

- 현재 topic 기본값: `orderFilled`
- 소비 멱등 키: `orderId`
- 일반 주문과 예약 주문 모두 최종 `OrderFilled`로 집계한다.
- 같은 사용자의 체결이 병렬 처리돼도 DB 원자 증가로 `trade_count`가 유실되지 않는다.

Debezium CDC topic 확정 후에는 코드가 아니라 다음 환경변수만 교체한다.

```text
RANKING_ORDER_FILLED_TOPIC
```

## 4. 향후 상태 이벤트 추가 시 변경 위치

현재는 모든 가입 사용자와 신규 계좌를 `ACTIVE`로 본다. 다음 계약이 추가되면 실제 상태 투영으로
교체한다.

```text
UserStatusChanged
- eventId
- userId
- status
- occurredAt

AccountStatusChanged
- eventId
- userId
- status
- occurredAt
```

변경 위치:

- `ranking/event`: 상태 event record와 consumer
- `RankingParticipant`: 사용자·계좌 상태 변경 메서드
- `RankingParticipantProjectionService`: 상태 투영 메서드
- `DefaultRankingParticipantProjectionService`: 상태와 소비 이력의 transaction 저장
- migration: `user_status_updated_at`, `account_status_updated_at`
- `RankingParticipant.fromProfile()`: 임시 ACTIVE 초기화 제거 검토

상태별 시각을 분리해야 서로 다른 topic의 늦게 도착한 이벤트가 최신 상태를 덮어쓰지 않는다.

## 5. 기본 실행

```bash
docker compose up -d postgres redpanda redis
./gradlew :services:ranking-service:bootRun
```

정상 로그:

```text
Started RankingServiceApplication
```

## 6. 자동 테스트

```bash
./gradlew :services:ranking-service:test
```

주요 기능만 빠르게 확인:

```bash
./gradlew :services:ranking-service:test \
--tests '*GrpcPortfolioSnapshotClientTest' \
--tests '*OrderFilledConsumerTest' \
--tests '*DefaultRankingParticipantProjectionServiceTest' \
--tests '*DefaultDailyRankingServiceTest' \
--tests '*RankingIdempotencyExecutorTest' \
--tests '*DefaultRankingQueryServiceTest'
```

## 7. 로컬 PostgreSQL·Redis 통합 테스트

### 거래 횟수 중복·동시 증가

```bash
RUN_LOCAL_RANKING_TRADE_TEST=true \
KEEP_LOCAL_RANKING_TRADE_TEST_DATA=true \
./gradlew :services:ranking-service:test \
--tests '*RankingTradeProjectionLocalIntegrationTest'
```

확인:

```bash
docker compose exec postgres \
psql -U candle -d candle_ranking -c "
SELECT user_id, trade_count, user_status, account_status
FROM ranking_participants
WHERE user_id = '81000000-0000-4000-8000-000000000001';
"
```

동시 체결 테스트가 마지막으로 실행됐다면 `trade_count=20`, 상태는 모두 `ACTIVE`여야 한다.

### 일별 랭킹 저장

```bash
RUN_LOCAL_RANKING_DB_TEST=true \
KEEP_LOCAL_RANKING_DB_TEST_DATA=true \
./gradlew :services:ranking-service:test \
--tests '*DailyRankingLocalIntegrationTest'
```

### 멱등성·Outbox

```bash
RUN_LOCAL_RANKING_COMMAND_DB_TEST=true \
KEEP_LOCAL_RANKING_COMMAND_DB_TEST_DATA=true \
./gradlew :services:ranking-service:test \
--tests '*RankingCommandLocalIntegrationTest'
```

### 조회·Redis

```bash
RUN_LOCAL_RANKING_QUERY_TEST=true \
KEEP_LOCAL_RANKING_QUERY_TEST_DATA=true \
./gradlew :services:ranking-service:test \
--tests '*RankingQueryLocalIntegrationTest'
```

```bash
docker compose exec redis redis-cli GET ranking:latest-date
docker compose exec redis redis-cli KEYS 'ranking:*'
```

## 8. Kafka 수신 수동 확인

Ranking Service를 실행한 뒤 테스트 전용 사용자가 `ranking_participants`에 존재하는 상태에서
다음 payload를 발행한다.

```bash
printf '%s\n' '{
  "orderId":"82000000-0000-4000-8000-000000000099",
  "userId":"81000000-0000-4000-8000-000000000001",
  "symbol":"005930",
  "side":"BUY",
  "executedPriceKrw":80000,
  "executedQuantity":1,
  "feeKrw":10,
  "taxKrw":0,
  "netAmountKrw":80010
}' | docker compose exec -T redpanda rpk topic produce orderFilled
```

같은 payload를 두 번 발행한 뒤 다음을 확인한다.

```bash
docker compose exec postgres \
psql -U candle -d candle_ranking -c "
SELECT trade_count
FROM ranking_participants
WHERE user_id = '81000000-0000-4000-8000-000000000001';

SELECT source_service, event_id, event_type
FROM ranking_consumed_events
WHERE event_id = '82000000-0000-4000-8000-000000000099';
"
```

같은 `orderId`는 `trade_count`와 소비 이력에 한 번만 반영돼야 한다.

## 9. 테스트용 Fake 범위

- Portfolio Fake는 `DefaultDailyRankingServiceTest` 내부에만 존재한다.
- 운영에서는 `GrpcPortfolioSnapshotClient`만 사용한다.
- 테스트 사용자는 로컬 통합 테스트가 전용 UUID로 생성한다.
- Ranking 운영 코드에는 임시 수익률이나 성공을 반환하는 Fake가 없다.
