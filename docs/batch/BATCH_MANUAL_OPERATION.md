# Batch 전체 수동 실행 및 운영 전환 가이드

## 1. 목적과 현재 전제

이 문서는 Candle Batch의 모든 Job을 자동 스케줄 없이 수동으로 실행하고, 호출 흐름과
DB 결과를 확인하는 절차를 설명한다.

- Batch 운영 인스턴스는 1개다.
- 현재는 애플리케이션 분산락을 추가하지 않는다.
- Spring Batch 메타데이터는 PostgreSQL `candle.batch` schema에 영구 보관한다.
- `BATCH_*_ENABLED=false`는 cron 자동 실행만 끄며 Batch Control 수동 실행은 막지 않는다.
- Batch는 다른 서비스 DB를 직접 변경하지 않고 gRPC로 소유 서비스에 명령한다.
- 운영 코드에는 Fake가 없다. 대상 데이터가 없으면 처리 건수 0과 `COMPLETED`가 정상일 수 있다.

## 2. 수동 실행 가능한 Job

| 순번 | Job | 입력 파라미터 | 주요 의존 서비스 |
| --- | --- | --- | --- |
| 1 | `batchSmokeJob` | `runId` | Batch DB |
| 2 | `stockSyncJob` | `runId` | Stock, Kiwoom |
| 3 | `tradingPreviousCloseJob` | `businessDate` | Trading |
| 4 | `tradingOpenLimitJob` | `businessDate` | Trading |
| 5 | `tradingExpirePendingOrdersJob` | `businessDate` | Trading |
| 6 | `tradingFailStaleConvertingJob` | `businessDate` | Trading |
| 7 | `tradingTodayCloseJob` | `businessDate` | Stock, Trading |
| 8 | `tradingExpireReservationsJob` | `businessDate` | Trading |
| 9 | `portfolioEodSnapshotJob` | `businessDate` | Portfolio, Trading, Stock |
| 10 | `dailyRankingFinalizeJob` | `rankingDate` | Ranking, Portfolio |

Batch Control gRPC 포트는 `50062`다. 모든 `TriggerJob` 요청에는 요청 추적 및 중복
제어를 위한 `command_metadata.idempotency_key`가 필요하다.

## 3. 어떤 서비스를 켜야 하는가

### 3.1 전체 Job 실행

| 구성요소 | 주소 | 필요한 이유 |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | 모든 서비스 DB와 Spring Batch 실행 이력 |
| Redis | `localhost:6379` | Ranking 조회 캐시 |
| Redpanda/Kafka | `localhost:9092` | Trading·Portfolio·Ranking 이벤트와 Outbox |
| Stock Service | gRPC `50060` | 종목 동기화와 일봉 종가 확정·조회 |
| Trading Service | gRPC `50054` | 예약·주문·잔고 처리 |
| Portfolio Service | gRPC `50055` | 활성 보유자와 EOD 스냅샷 |
| Ranking Service | gRPC `50056` | 일별 랭킹 확정 |
| Batch | gRPC `50062` | Job 등록, 자동·수동 실행과 메타데이터 관리 |

Market Service `50063`은 실시간 체결·시세까지 포함한 Trading 전체 흐름을 만들 때
필요하다. 현재 수동 Batch Job 자체는 Stock의 확정 일봉 계약을 주로 사용한다.

### 3.2 Job별 최소 구성

- `batchSmokeJob`: PostgreSQL, Batch
- `stockSyncJob`: PostgreSQL, Stock, Batch, 실제 동기화 시 Kiwoom key/secret
- Trading 6개 Job: PostgreSQL, Trading, Batch
- `tradingTodayCloseJob`: PostgreSQL, Trading, Stock, Batch
- `portfolioEodSnapshotJob`: PostgreSQL, Trading, Portfolio, Stock, Batch
- `dailyRankingFinalizeJob`: PostgreSQL, Redis, Kafka, Portfolio, Ranking, Batch

## 4. 로컬 서비스 기동

### 4.1 인프라

BFF와 Gateway는 Batch 검증에 필요하지 않다.

```bash
docker compose up -d postgres redis redpanda
docker compose ps
```

기존 Docker volume이 잘못 초기화되어 `candle` DB 또는 schema가 없다면 먼저 팀과
데이터 보존 여부를 확인한다. 운영 데이터를 임의로 삭제하지 않는다.

### 4.2 백엔드 서비스

각 명령은 별도 터미널에서 실행한다.

```bash
STOCK_GRPC_PORT=50060 \
./gradlew :services:stock-service:bootRun
```

```bash
TRADING_GRPC_PORT=50054 \
CHART_SERVICE_GRPC_ADDRESS=static://localhost:50060 \
MARKET_SERVICE_GRPC_ADDRESS=static://localhost:50063 \
./gradlew :services:trading-service:bootRun
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

### 4.3 Batch

수동 검증 중에는 모든 업무 cron과 Smoke cron을 끈다.

```bash
BATCH_SMOKE_ENABLED=false \
BATCH_TRADING_ENABLED=false \
BATCH_PORTFOLIO_EOD_ENABLED=false \
BATCH_STOCK_SYNC_ENABLED=false \
BATCH_RANKING_ENABLED=false \
BATCH_TRADING_GRPC_TARGET=static://localhost:50054 \
BATCH_PORTFOLIO_GRPC_TARGET=static://localhost:50055 \
BATCH_STOCK_GRPC_TARGET=static://localhost:50060 \
BATCH_MARKET_GRPC_TARGET=static://localhost:50060 \
BATCH_RANKING_GRPC_TARGET=static://localhost:50056 \
BATCH_GRPC_PORT=50062 \
./gradlew :batch:bootRun
```

`SPRING_BATCH_JOB_ENABLED=false`는 애플리케이션 시작 시 Job 자동 기동을 막는다. Batch
Control을 통한 수동 실행에는 영향을 주지 않는다.

## 5. Batch Control 호출 도구

로컬에 `grpcurl`이 설치되어 있으면 직접 사용한다. 설치되어 있지 않으면 Docker
이미지를 사용할 수 있다.

### 5.1 Docker 기반 공통 함수

저장소 루트에서 다음 함수를 현재 shell에 등록한다.

```bash
trigger_job() {
  JOB_NAME="$1"
  PARAM_NAME="$2"
  PARAM_VALUE="$3"
  REQUEST_KEY="$4"

  docker run --rm \
    -v "$PWD/proto:/proto:ro" \
    fullstorydev/grpcurl:latest \
    -plaintext \
    -import-path /proto \
    -proto candle/batch/v1/batch.proto \
    -d "{\"jobName\":\"${JOB_NAME}\",\"parameters\":{\"${PARAM_NAME}\":\"${PARAM_VALUE}\"},\"commandMetadata\":{\"idempotencyKey\":\"${REQUEST_KEY}\"}}" \
    host.docker.internal:50062 \
    candle.batch.v1.BatchControlService/TriggerJob
}
```

`uuidgen` 결과는 각 수동 실행 의도마다 새로 만든다. 네트워크 재시도로 같은 요청을
다시 보낼 때는 같은 UUID를 사용한다.

### 5.2 Job 목록 확인

```bash
docker run --rm \
  -v "$PWD/proto:/proto:ro" \
  fullstorydev/grpcurl:latest \
  -plaintext \
  -import-path /proto \
  -proto candle/batch/v1/batch.proto \
  -d '{}' \
  host.docker.internal:50062 \
  candle.batch.v1.BatchControlService/ListJobs
```

10개 Job이 나오고 각 Job의 `triggerable`이 `true`여야 한다.

## 6. 수동 실행 순서와 명령어

아래 예시는 거래일 `2026-07-07`이다. 실제 확인할 날짜로 바꾼다.

### 6.1 기본 인프라 확인

```bash
trigger_job batchSmokeJob runId "$(date +%s)" "$(uuidgen)"
```

### 6.2 종목 마스터 동기화

```bash
trigger_job stockSyncJob runId "$(date +%s)" "$(uuidgen)"
```

Kiwoom key가 없으면 Stock Service 정책에 따라 0건으로 완료될 수 있다. 실제 외부
동기화를 검증하려면 인프라가 제공한 `KIWOOM_APP_KEY`, `KIWOOM_APP_SECRET`이 Stock
Service에 설정되어야 한다.

### 6.3 Trading Job

```bash
trigger_job tradingPreviousCloseJob businessDate 2026-07-07 "$(uuidgen)"
trigger_job tradingOpenLimitJob businessDate 2026-07-07 "$(uuidgen)"
trigger_job tradingExpirePendingOrdersJob businessDate 2026-07-07 "$(uuidgen)"
trigger_job tradingFailStaleConvertingJob businessDate 2026-07-07 "$(uuidgen)"
trigger_job tradingTodayCloseJob businessDate 2026-07-07 "$(uuidgen)"
trigger_job tradingExpireReservationsJob businessDate 2026-07-07 "$(uuidgen)"
```

`tradingExpirePendingOrdersJob` 다음 `tradingFailStaleConvertingJob`,
`tradingTodayCloseJob` 다음 `tradingExpireReservationsJob` 순서를 지킨다. 수동 호출은
이 선후 관계를 자동으로 묶지 않는다.

### 6.4 Portfolio EOD

```bash
trigger_job portfolioEodSnapshotJob businessDate 2026-07-07 "$(uuidgen)"
```

Trading 현금, Portfolio 활성 보유자, Stock 확정 종가가 준비된 뒤 실행한다.

### 6.5 Ranking 확정

```bash
trigger_job dailyRankingFinalizeJob rankingDate 2026-07-07 "$(uuidgen)"
```

같은 날짜의 `portfolioEodSnapshotJob`이 Batch DB에서 `COMPLETED`여야 한다. 그렇지
않으면 Ranking RPC를 호출하지 않고 Job이 `FAILED`가 된다.

## 7. 각 Job의 데이터 흐름과 저장 결과

### 7.1 `batchSmokeJob`

```text
Batch Control -> Smoke Job -> Batch metadata
```

외부 서비스 데이터는 읽지 않는다. Batch DB에 Job/Step 실행 이력만 남긴다.

### 7.2 `stockSyncJob`

```text
Batch -> Stock SyncStocks(KOSPI) -> Kiwoom -> stock.stocks upsert
      -> Stock SyncStocks(KOSDAQ) -> Kiwoom -> stock.stocks upsert
```

Batch는 Stock DB를 직접 읽지 않는다. Stock Service가 종목 코드·이름·시장·상장 상태
등을 `stock.stocks`에 upsert한다. 같은 종목은 중복 insert하지 않는다.

### 7.3 `tradingPreviousCloseJob`

`reservation.reservations`에서 해당 날짜의 `RESERVED + PREV_CLOSE` 대상을 Trading
Service가 조회하고 처리한다. 상태 변경과 Outbox는 Trading transaction에서 저장한다.

### 7.4 `tradingOpenLimitJob`

해당 날짜의 `RESERVED + OPEN + LIMIT` 예약을 주문 전환 흐름으로 넘긴다. 예약 상태,
`converted_order_id`, `order_svc.orders`, Outbox가 결과 확인 대상이다.

### 7.5 `tradingExpirePendingOrdersJob`

남아 있는 `order_svc.orders.status=PENDING` 주문을 만료·취소한다. 상태가 이미 변한
주문은 서비스 멱등 정책에 따라 다시 변경하지 않는다.

### 7.6 `tradingFailStaleConvertingJob`

전환 중 멈춘 `CONVERTING` 예약 ID 목록을 조회하고 건별로 실패 처리한다. 이미 다른
상태로 변경된 예약은 skip한다.

### 7.7 `tradingTodayCloseJob`

```text
Batch -> Stock CloseDailyCandles
      -> Trading ProcessTodayCloseReservations
      -> ReservationExecuted/OrderFilled Outbox
```

Stock Service가 일봉을 확정한 뒤 Trading Service가 `TODAY_CLOSE` 예약을 처리한다.

### 7.8 `tradingExpireReservationsJob`

해당 거래일 이후에도 남은 `RESERVED` 예약을 조회해 건별 `EXPIRED` 처리한다.

### 7.9 `portfolioEodSnapshotJob`

```text
Portfolio ListActiveHolders(active=true, quantity>0)
  -> Stock GetPreviousClose
  -> Batch closing-price stage
  -> Trading GetBalance
  -> total_asset 계산
  -> Portfolio RecordDailySnapshot
  -> portfolio.portfolio_snapshots
```

`stock_value=sum(quantity*closing_price)`,
`cash=available_cash+reserved_balance`, `total_asset=cash+stock_value`를 사용한다.
Portfolio Service가 전일 스냅샷과 1억 원 초기 원금 정책으로 손익과 누적 수익률을
저장한다. `(user_id, snapshot_date)`와 결정적 키로 재실행 중복을 막는다.

### 7.10 `dailyRankingFinalizeJob`

```text
Batch EOD COMPLETED 확인
  -> Ranking FinalizeDailyRanking
  -> Portfolio ListDailyPortfolioSnapshots
  -> 대상자 필터/정렬
  -> ranking_runs + ranking_snapshot + ranking_history
  -> ranking_idempotency_records + ranking_outbox_events
```

Ranking Service는 거래 5회 이상, 사용자·계좌 `ACTIVE` 대상만 포함하고
`profit_rate DESC`, `trade_count DESC`, `user_id ASC`로 순위를 확정한다.

## 8. 결과 확인

### 8.1 모든 Batch 실행 상태

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT ji.job_name,
       je.job_execution_id,
       je.status,
       je.exit_code,
       je.start_time,
       je.end_time,
       je.exit_message
FROM batch.batch_job_execution je
JOIN batch.batch_job_instance ji
  ON ji.job_instance_id = je.job_instance_id
ORDER BY je.job_execution_id DESC
LIMIT 50;
"
```

정상 실행은 `status=COMPLETED`, `exit_code=COMPLETED`다. 대상 데이터가 0건이어도
업무 오류가 없다면 `COMPLETED`가 정상이다.

### 8.2 Step 처리량

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT step_name, status, read_count, write_count,
       commit_count, rollback_count, exit_message
FROM batch.batch_step_execution
ORDER BY step_execution_id DESC
LIMIT 50;
"
```

Tasklet Job은 `read_count/write_count=0`일 수 있다. 처리 건수는 해당 Job 로그와 소유
서비스 DB도 함께 확인한다.

### 8.3 Stock 결과

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT market_type, COUNT(*) AS stock_count, MAX(synced_at) AS last_synced_at
FROM stock.stocks
GROUP BY market_type
ORDER BY market_type;

SELECT stock_code, stock_name, market_type, listing_status,
       data_source, synced_at
FROM stock.stocks
ORDER BY synced_at DESC NULLS LAST
LIMIT 20;
"
```

### 8.4 Trading 결과

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT id, user_id, symbol, timing, order_kind,
       scheduled_date, status, converted_order_id, updated_at
FROM reservation.reservations
WHERE scheduled_date = DATE '2026-07-07'
ORDER BY id;

SELECT id, user_id, symbol, order_kind, status, updated_at
FROM order_svc.orders
ORDER BY updated_at DESC
LIMIT 30;

SELECT event_type, aggregate_id, occurred_at, published_at
FROM reservation.outbox_events
ORDER BY occurred_at DESC
LIMIT 20;

SELECT event_type, aggregate_id, occurred_at, published_at
FROM order_svc.outbox_events
ORDER BY occurred_at DESC
LIMIT 20;
"
```

### 8.5 Portfolio EOD 결과

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT user_id, symbol, quantity, average_price, active
FROM portfolio.portfolio_holdings
WHERE active = TRUE AND quantity > 0
ORDER BY user_id, symbol;

SELECT user_id, snapshot_date, total_asset, stock_value,
       daily_profit, cumulative_return_rate, created_at
FROM portfolio.portfolio_snapshots
WHERE snapshot_date = DATE '2026-07-07'
ORDER BY user_id;
"
```

활성 보유자가 있는데 스냅샷이 없다면 Stock 확정 종가와 Trading 계좌 존재 여부부터
확인한다.

### 8.6 Ranking 결과

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT ranking_date, ranked_user_count, completed_at
FROM ranking.ranking_runs
WHERE ranking_date = DATE '2026-07-07';

SELECT user_id, ranking_position, total_asset,
       profit_rate, trade_count, ranking_date
FROM ranking.ranking_history
WHERE ranking_date = DATE '2026-07-07'
ORDER BY ranking_position;

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

대상자가 0명이면 `ranking_runs.ranked_user_count=0`과 빈 `ranking_history`가 정상이다.

## 9. 의미 있는 테스트 데이터를 만드는 방법

임의 SQL insert로 계좌·예약·체결 불변식을 우회하지 않는다. 정상 gRPC/BFF 흐름으로
사용자·계좌·예약·주문·체결을 만든다.

- Stock Sync만 확인: Kiwoom key를 설정하거나 기존 seed 종목을 확인한다.
- Trading 상태 변경 확인: 해당 날짜의 예약 또는 PENDING 주문이 필요하다.
- Portfolio EOD 확인: Trading 계좌, Portfolio 활성 보유, Stock 확정 일봉이 모두 필요하다.
- Ranking 확인: Portfolio EOD 스냅샷과 거래 횟수 5회 이상 ACTIVE 참가자가 필요하다.

대상이 준비되지 않은 초기 환경에서는 먼저 모든 Job이 0건 `COMPLETED`되는지 확인하고,
그 다음 정상 업무 API로 데이터를 만든 뒤 상태 변경 결과를 확인한다.

## 10. 실패와 재실행

1. `batch.batch_job_execution.exit_message`에서 실패 지점을 확인한다.
2. 로그의 gRPC status와 대상 서비스 주소를 확인한다.
3. 소유 서비스 DB와 미발행 Outbox를 확인한다.
4. 원인을 복구한다.
5. 실패 Job은 같은 거래일 파라미터로 다시 호출한다.
6. 네트워크 재전송이라면 동일 Batch Control idempotency key를 사용한다.
7. 이미 완료된 같은 JobInstance는 `ALREADY_EXISTS`가 정상이다.

Ranking Job은 EOD부터 복구하고, Trading 후행 Job은 선행 Job의 완료를 확인한 뒤
재실행한다.

## 11. 실제 인프라 준비 후 코드 변경 여부

Batch 인스턴스가 1개로 보장되므로 현재 Java 코드에 분산락을 추가하지 않는다.
일반적인 운영 전환은 코드 수정이 아니라 환경변수와 배포 설정 변경이다.

### 11.1 필수 운영 설정

```bash
BATCH_SMOKE_ENABLED=false
BATCH_TRADING_ENABLED=true
BATCH_PORTFOLIO_EOD_ENABLED=true
BATCH_RANKING_ENABLED=true
BATCH_STOCK_SYNC_ENABLED=true

BATCH_TRADING_GRPC_TARGET=static://trading-service:50054
BATCH_PORTFOLIO_GRPC_TARGET=static://portfolio-service:50055
BATCH_STOCK_GRPC_TARGET=static://stock-service:50060
BATCH_MARKET_GRPC_TARGET=static://stock-service:50060
BATCH_RANKING_GRPC_TARGET=static://ranking-service:50056
```

함께 설정할 항목:

- `BATCH_DB_URL`, `BATCH_DB_USERNAME`, `BATCH_DB_PASSWORD`
- 서비스별 PostgreSQL 접속 정보
- Kafka bootstrap server와 Redis 주소
- Stock Service의 Kiwoom key/secret
- Batch replica `1`
- Batch DB 영구 보존
- 서비스 readiness와 로그·알림

### 11.2 기본 운영 cron

```text
08:30 tradingPreviousCloseJob
09:00 tradingOpenLimitJob
15:30 tradingExpirePendingOrdersJob -> tradingFailStaleConvertingJob
15:40 tradingTodayCloseJob -> tradingExpireReservationsJob
16:00 portfolioEodSnapshotJob
16:20 dailyRankingFinalizeJob
16:30 stockSyncJob
```

### 11.3 코드 변경이 필요한 경우

다음 조건에서만 추가 코드·설정 개발을 검토한다.

- Batch replica가 2개 이상이 되면 분산락 또는 플랫폼 단일 실행 보장
- 내부 gRPC가 TLS/mTLS를 요구하면 channel SSL 설정과 인증서 주입
- 서비스 DNS 또는 포트 계약이 바뀌면 환경변수 수정
- 공휴일·임시 휴장일 자동 차단이 필요하면 거래일 캘린더 연동
- 운영 처리 시간이 deadline을 넘으면 timeout 및 성능 정책 조정
- Micrometer 기반 처리 건수·실패 알림이 필요하면 관측성 추가

현재 전제에서는 Java Job 로직을 고치는 것이 아니라 staging 수동 검증을 통과한 뒤
운영 환경변수의 `ENABLED` 값을 켜는 것이 다음 단계다.
