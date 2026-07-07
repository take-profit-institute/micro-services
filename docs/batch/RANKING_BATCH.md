# Daily Ranking Finalization Batch

## 1. 목적과 책임

`dailyRankingFinalizeJob`은 Portfolio EOD 스냅샷 생성이 완료된 KST 거래일에만
Ranking Service의 `FinalizeDailyRanking`을 호출한다.

```text
16:00 Portfolio EOD COMPLETED
  -> 16:20 RankingFinalizeJobScheduler
  -> dailyRankingFinalizeJob
  -> PortfolioEodCompletionGuard
  -> RankingService/FinalizeDailyRanking
  -> Ranking DB + Outbox + idempotency record
```

Batch는 랭킹을 직접 계산하거나 Ranking DB를 변경하지 않는다. Batch가 소유하는 것은
실행 시각, 선행 Job 확인, 재시도와 Spring Batch 실행 이력이다. 대상자 필터링, 순위
계산, 저장, Outbox와 멱등성 트랜잭션은 Ranking Service가 소유한다.

운영 코드에는 Fake 사용자가 없으며 테스트에서만 gRPC client를 Mock으로 교체한다.

## 2. 외부 계약

| 항목 | 값 |
| --- | --- |
| 서비스 | `candle.ranking.v1.RankingService` |
| RPC | `FinalizeDailyRanking` |
| 기본 주소 | `static://localhost:50056` |
| 요청 | `ranking_date`, `command_metadata.idempotency_key` |
| 응답 | `ranking_date`, `ranked_user_count` |
| 인증 metadata | `x-user-id=batch-service`, `x-role=SYSTEM` |
| 멱등성 metadata | `x-idempotency-key` |

request의 key와 metadata의 key는 항상 동일하다. 원본 키 의미는
`ranking-finalize:<rankingDate>`이며 Ranking Service가 요구하는 canonical UUID 형식에
맞춰 이름 기반 UUID로 변환한다. 따라서 같은 거래일은 실행·재시작 여부와 관계없이
항상 같은 UUID를 사용한다.

## 3. 실행 순서와 선행 조건

| 시각(KST) | Job | 관계 |
| --- | --- | --- |
| 15:40 | Trading today-close | 확정 일봉과 종가 예약 처리 |
| 16:00 | `portfolioEodSnapshotJob` | 당일 Portfolio 스냅샷 저장 |
| 16:20 | `dailyRankingFinalizeJob` | EOD 완료 후 당일 랭킹 확정 |
| 16:30 | `stockSyncJob` | 종목 마스터 동기화 |

Ranking Job은 Spring Batch 메타데이터에서 같은 `businessDate`의
`portfolioEodSnapshotJob` 실행 중 `COMPLETED`가 하나라도 있는지 확인한다. EOD를
Batch Control로 실행해 `runId`가 추가됐더라도 거래일과 상태로 찾는다.

선행 EOD가 없거나 `FAILED`, `STOPPED`, `ABANDONED`이면 Ranking RPC를 호출하지 않고
Ranking Job을 `FAILED`로 종료한다. EOD 대상자가 0명이더라도 EOD가 정상 완료됐다면
Ranking 실행이 가능하며, Ranking의 `ranked_user_count=0`도 정상 결과다.

## 4. 멱등성·재시도·재시작 정책

| 항목 | 정책 |
| --- | --- |
| JobInstance | `jobName + rankingDate` |
| 같은 날짜 완료 Job | Spring Batch가 중복 실행 차단 |
| 실패 Job | 같은 `rankingDate`로 재시작 |
| downstream key | 같은 날짜에 항상 같은 결정적 UUID |
| 일시적 gRPC 오류 | 최대 3회, 같은 key로 재시도 |
| 영구 gRPC 오류 | 즉시 Step과 Job 실패 |
| EOD 미완료 | RPC 미호출, Job 실패 |

재시도 대상 gRPC code는 `INTERNAL`, `UNAVAILABLE`, `DEADLINE_EXCEEDED`,
`RESOURCE_EXHAUSTED`, `ABORTED`다. 입력 오류, 권한 오류, 멱등성 충돌과 응답 검증
오류는 자동 재시도하지 않는다.

Ranking Service는 랭킹 저장, `ranking_runs`, Outbox, 성공 response와
idempotency record를 하나의 DB transaction으로 처리한다. Batch의 Step transaction은
이 원격 transaction을 대신하지 않는다.

자동 스케줄은 오늘 날짜만 실행한다. 과거 날짜의 실패 Job은 원인을 복구한 후 Batch
Control에서 같은 `rankingDate`로 수동 재실행한다.

## 5. 운영 설정

기본값은 평일 16:20 KST이며 안전하게 비활성화되어 있다.

```bash
BATCH_RANKING_ENABLED=true
BATCH_RANKING_CRON="0 20 16 * * MON-FRI"
BATCH_RANKING_GRPC_TARGET=static://ranking-service:50056
BATCH_RANKING_DEADLINE_MILLIS=120000
```

로컬에서는 target을 `static://localhost:50056`으로 사용한다. Batch Control은 `50062`,
Portfolio는 `50055`, Trading은 `50054`, Stock은 `50060`을 사용한다.

cron은 주말만 제외한다. 공휴일과 임시 휴장일은 거래일 캘린더 계약이 생기기 전까지
자동 판별하지 않는다. 해당 날짜에 EOD가 실행되지 않으면 선행 조건 검증으로 Ranking
Job도 실패한다.

현재 Batch 다중 인스턴스 분산 잠금은 없다. 운영은 단일 Batch 인스턴스를 전제로 하며,
다중 인스턴스 배포 전에는 ShedLock 또는 플랫폼의 단일 스케줄 실행 정책이 필요하다.

## 6. 자동 테스트

Ranking Batch 전용 테스트:

```bash
./gradlew :batch:test --tests '*ranking*'
```

전체 Batch 회귀 테스트:

```bash
./gradlew :batch:test
```

2026-07-06 기준 전체 Batch 테스트 결과는 63개 성공, 실패 0개다. 검증 범위는 다음과
같다.

- gRPC metadata와 request의 멱등성 키 일치
- 응답 날짜와 처리 인원 검증
- 결정적 UUID 생성
- 동일 거래일 EOD 완료 여부 확인
- EOD 미완료 시 Ranking RPC 미호출
- 일시적 오류 최대 3회와 영구 오류 즉시 실패
- 같은 날짜 완료 Job 중복 실행 차단
- 실패한 같은 날짜 Job 재시작
- 16:20 자동 실행의 KST 날짜 파라미터
- Batch Control 수동 실행
- EOD, Trading, Stock, Ranking 전체 Batch 회귀

Spring 통합 테스트는 `spring.grpc.server.port=0`으로 빈 임시 포트를 사용하므로 운영
Batch Control 포트 `50062`와 충돌하지 않는다.

## 7. 로컬 실제 연동 테스트

### 7.1 서비스 실행

인프라와 필요한 서비스를 실행한다.

```bash
docker compose up -d postgres redis redpanda
```

```bash
PORTFOLIO_GRPC_PORT=50055 \
./gradlew :services:portfolio-service:bootRun
```

```bash
RANKING_GRPC_PORT=50056 \
RANKING_PORTFOLIO_GRPC_TARGET=static://localhost:50055 \
./gradlew :services:ranking-service:bootRun
```

EOD부터 함께 검증하려면 Trading `50054`와 Stock `50060`도 실행한다. Ranking만 검증할
때는 같은 날짜의 EOD Batch 실행 이력과 Portfolio 스냅샷이 이미 존재해야 한다.

### 7.2 자동 스케줄 실행

EOD와 Ranking cron을 서로 다른 초로 지정하면 기다리지 않고 순서를 검증할 수 있다.

```bash
BATCH_PORTFOLIO_EOD_ENABLED=true \
BATCH_PORTFOLIO_EOD_CRON="10 * * * * *" \
BATCH_RANKING_ENABLED=true \
BATCH_RANKING_CRON="40 * * * * *" \
BATCH_PORTFOLIO_GRPC_TARGET=static://localhost:50055 \
BATCH_TRADING_GRPC_TARGET=static://localhost:50054 \
BATCH_STOCK_GRPC_TARGET=static://localhost:50060 \
BATCH_RANKING_GRPC_TARGET=static://localhost:50056 \
./gradlew :batch:bootRun
```

정상 로그 순서는 Portfolio EOD `COMPLETED` 다음
`[Daily Ranking] finalized`다. Ranking 로그의 `rankedUserCount`는
`ranking_runs.ranked_user_count`와 같아야 한다.

## 8. Batch Control 수동 실행

Batch Control의 `ListJobs`에는 `dailyRankingFinalizeJob`이 노출되고 지원 파라미터는
`rankingDate`다. 상태 변경 호출이므로 Batch Control 요청 자체의
`command_metadata.idempotency_key`도 필수다.

```bash
grpcurl -plaintext \
  -import-path proto \
  -proto candle/batch/v1/batch.proto \
  -d '{
    "jobName": "dailyRankingFinalizeJob",
    "parameters": {"rankingDate": "2026-07-06"},
    "commandMetadata": {
      "idempotencyKey": "0197d249-1290-7000-8000-000000000001"
    }
  }' \
  localhost:50062 \
  candle.batch.v1.BatchControlService/TriggerJob
```

같은 날짜의 EOD가 완료되지 않았다면 요청은 시작되더라도 Job 결과는 `FAILED`다. 같은
날짜 Ranking Job이 이미 완료됐다면 `ALREADY_EXISTS`가 정상이다.

## 9. DB 검증

### 9.1 Batch 실행 이력

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT ji.job_instance_id, ji.job_name,
       je.job_execution_id, je.status, je.exit_code,
       je.start_time, je.end_time, je.exit_message
FROM batch.batch_job_execution je
JOIN batch.batch_job_instance ji
  ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name IN ('portfolioEodSnapshotJob', 'dailyRankingFinalizeJob')
ORDER BY je.job_execution_id DESC
LIMIT 20;
"
```

정상 결과는 같은 거래일의 EOD가 먼저 `COMPLETED`, Ranking이 다음 `COMPLETED`다.

### 9.2 Ranking 저장 결과

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT ranking_date, ranked_user_count, completed_at
FROM ranking.ranking_runs
ORDER BY ranking_date DESC
LIMIT 10;

SELECT user_id, ranking_position, total_asset,
       profit_rate, trade_count, ranking_date
FROM ranking.ranking_history
ORDER BY ranking_date DESC, ranking_position
LIMIT 100;
"
```

`ranking_runs`에는 대상자가 0명인 날도 완료 행이 있어야 한다. `ranking_history`는
거래 5회 이상이고 사용자·계좌 상태가 모두 `ACTIVE`인 대상만 포함해야 한다.

### 9.3 멱등성·Outbox 확인

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT actor_id, operation, idempotency_key, created_at, expires_at
FROM ranking.ranking_idempotency_records
ORDER BY created_at DESC
LIMIT 10;

SELECT event_id, event_type, aggregate_id, occurred_at, published_at
FROM ranking.ranking_outbox_events
ORDER BY occurred_at DESC
LIMIT 10;
"
```

정상 실행 한 번에는 해당 거래일의 성공 idempotency record와 Ranking Outbox가 한 번만
기록된다. 같은 날짜 재호출은 저장된 응답을 재생하며 랭킹과 Outbox를 중복 생성하지
않는다.

## 10. 장애 확인과 재실행 순서

1. Batch DB에서 EOD와 Ranking Job의 상태 및 `exit_message`를 확인한다.
2. EOD가 `COMPLETED`가 아니면 Ranking보다 EOD를 먼저 복구·재실행한다.
3. Batch 로그에서 Ranking gRPC code와 최대 3회 재시도 결과를 확인한다.
4. Ranking Service의 Portfolio 연결, PostgreSQL, Redis, Kafka 상태를 확인한다.
5. Ranking DB에 일부 데이터만 저장됐는지 확인한다. 정상 transaction이면 부분 저장은
   없어야 한다.
6. 원인을 복구한 뒤 같은 `rankingDate`로 Batch Control에서 재실행한다.
7. 완료된 Job을 다시 실행했을 때 `ALREADY_EXISTS`면 추가 조치하지 않는다.

Redis 장애는 Ranking Service의 조회 캐시에 영향을 주지만 DB 랭킹 확정 transaction을
대체하지 않는다. Outbox 발행 지연은 `published_at IS NULL`로 확인하며 Batch에서 Kafka에
직접 재발행하지 않는다.

## 11. 향후 변경 지점

1. 거래일 캘린더 계약이 생기면 공휴일·임시 휴장일 실행을 Scheduler에서 차단한다.
2. Batch 다중 인스턴스 운영 전에 분산 실행 잠금을 추가한다.
3. 실제 운영 처리 시간이 길어지면 16:20 시작 시각과 120초 gRPC deadline을 조정한다.
4. Portfolio EOD Job 이름 또는 날짜 파라미터가 바뀌면
   `PortfolioEodCompletionGuard`의 계약을 함께 변경한다.
5. Ranking RPC의 actor 또는 idempotency 규칙이 바뀌면
   `GrpcRankingBatchClient`와 결정적 키 테스트를 함께 수정한다.
6. 처리 건수·실패 횟수는 Micrometer metric과 운영 알림에 연결한다.
