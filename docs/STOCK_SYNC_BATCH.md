# Stock 종목 마스터 Sync Batch

## 1. 목적과 책임

`stockSyncJob`은 평일 장 마감 후 Stock Service의 `SyncStocks` RPC를 호출해
KOSPI와 KOSDAQ 종목 마스터 동기화를 시작한다.

```text
Batch stockSyncJob
  -> StockService.SyncStocks(KOSPI)
  -> StockService.SyncStocks(KOSDAQ)
  -> Stock Service가 키움 조회와 stocks upsert 수행
```

Batch는 실행 시점, 시장 순서, 재시도와 실행 이력만 관리한다. 키움 인증,
연속조회, 응답 파싱, `candle_stock.stocks` 저장은 Stock Service 책임이다.
Batch가 Stock DB 또는 키움 API에 직접 접근하지 않는다.

## 2. 현재 구현

| 항목 | 현재 정책 |
| --- | --- |
| Job | `stockSyncJob` |
| Step | `stockSyncStep` |
| 실행 순서 | KOSPI 완료 후 KOSDAQ |
| 기본 스케줄 | 평일 16:30 KST |
| 기본 활성화 | `false` |
| Stock gRPC 포트 | `50060` |
| 호출 제한시간 | 시장별 120초 |
| 재시도 | 재시도 가능한 오류에 시장별 최대 3회 |
| 멱등성 | Stock Service의 `stock_code` upsert 사용 |
| 다중 인스턴스 잠금 | 현재 없음. 단일 Batch 인스턴스 전제 |

`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `ABORTED`,
`INTERNAL`은 재시도한다. 입력 오류처럼 재시도해도 결과가 달라지지 않는 오류는
즉시 Job을 실패시킨다.

KOSPI가 성공하고 KOSDAQ이 최종 실패하면 Job 상태는 `FAILED`다. 재실행하면
KOSPI부터 다시 호출하지만 upsert이므로 중복 행은 생성되지 않는다.

## 3. 코드 흐름

```text
StockSyncJobScheduler
  -> JobParameterFactory.createRunParameters
  -> JobOperator.start(stockSyncJob)
  -> StockSyncTasklet
  -> GrpcStockSyncClient
  -> candle.stock.v1.StockService/SyncStocks
```

- `GrpcStockSyncClient`: proto 요청 변환, deadline, gRPC 오류 분류
- `StockSyncTasklet`: 시장 실행 순서, 최대 3회 재시도, 처리 건수 로그
- `StockSyncJobConfiguration`: Spring Batch 6 Job과 Step 정의
- `StockSyncJobScheduler`: 설정된 cron에 Job 시작
- `BatchControlGrpcService`: Job 목록, 수동 실행, 실행 결과 조회

운영 코드에는 Fake 또는 임시 종목 데이터가 없다. 외부 호출 대체는 테스트에서만
사용한다.

## 4. 설정

```bash
BATCH_STOCK_SYNC_ENABLED=true
BATCH_STOCK_SYNC_CRON="0 30 16 * * MON-FRI"
BATCH_STOCK_GRPC_TARGET=static://localhost:50060
BATCH_STOCK_SYNC_DEADLINE_MILLIS=120000
```

Stock Service가 다른 포트로 실행되는 환경에서는
`BATCH_STOCK_GRPC_TARGET`만 변경한다. 현재 프로젝트의 확정 기본 포트는
`50060`이다.

## 5. 테스트

### 자동 테스트

```bash
./gradlew :batch:test --tests '*stock.sync*'
```

검증 범위는 다음과 같다.

- KOSPI/KOSDAQ proto 변환과 응답 건수 매핑
- KOSPI 다음 KOSDAQ 실행
- 일시적 오류 재시도와 3회 실패
- Scheduler 활성/비활성 분기
- Spring Batch Job의 `COMPLETED` 상태

### 실제 서비스 연동

1. PostgreSQL을 실행한다.

```bash
docker compose up -d postgres
```

2. Stock Service를 실행한다. 실제 적재를 확인하려면 키움 인증값이 필요하다.

```bash
STOCK_GRPC_PORT=50060 \
./gradlew :services:stock-service:bootRun
```

3. 테스트할 때는 cron을 가까운 시각으로 바꾸고 Batch를 실행한다.

```bash
BATCH_STOCK_SYNC_ENABLED=true \
BATCH_STOCK_SYNC_CRON="0 * * * * *" \
BATCH_STOCK_GRPC_TARGET=static://localhost:50060 \
./gradlew :batch:bootRun
```

4. Batch 로그에서 시장별 결과를 확인한다.

```text
[Stock Sync] market=KOSPI, upserted=..., total=..., attempt=1
[Stock Sync] market=KOSDAQ, upserted=..., total=..., attempt=1
[Batch End] jobName=stockSyncJob, ... status=COMPLETED
```

5. Stock DB 결과를 확인한다.

```bash
docker compose exec postgres \
psql -U candle -d candle_stock -P pager=off -c "
SELECT market_type, COUNT(*)
FROM stocks
GROUP BY market_type
ORDER BY market_type;
"
```

키움 인증값이 없으면 Stock Service 정책에 따라 정상 응답으로 0건이 반환될 수
있다. 이 경우 Job 성공은 gRPC 연결과 실행 흐름만 검증하며 실제 종목 적재 성공을
의미하지 않는다.

6. Batch 실행 이력을 확인한다.

```bash
docker compose exec postgres \
psql -U candle -d candle_batch -P pager=off -c "
SELECT ji.job_name, je.status, je.exit_code, je.create_time, je.end_time
FROM batch_job_execution je
JOIN batch_job_instance ji ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name = 'stockSyncJob'
ORDER BY je.job_execution_id DESC
LIMIT 10;
"
```

## 6. 다른 장 마감 Job과의 순서

`stockSyncJob`은 종목 이름과 시장 같은 **종목 마스터**를 동기화한다. 주가 일봉을
확정하는 `CloseDailyCandles`와는 다른 Job이며, Trading 종가 체결의 선행 조건이
아니다.

향후 Batch 장 마감 흐름은 다음 순서로 맞춰야 한다.

| 순서 | 권장 시각 | Job/RPC | 상태 |
| --- | --- | --- | --- |
| 1 | 08:30 | Trading `ProcessPrevCloseReservations` | 미구현 |
| 2 | 09:00 | Trading `ProcessOpenLimitReservations` | 미구현 |
| 3 | 15:30 | Trading `ExpirePendingOrders` | 미구현 |
| 4 | 15:30 | Trading stale CONVERTING 정리 | 미구현 |
| 5 | 15:35 | Stock `CloseDailyCandles` | Batch 미구현 |
| 6 | 15:40 | Trading `ProcessTodayCloseReservations` | Batch 미구현 |
| 7 | 15:40 이후 | Trading 남은 RESERVED 만료 | 미구현 |
| 8 | 16:00 | Portfolio EOD snapshot | 외부 계약 대기 |
| 9 | 16:30 | Stock `stockSyncJob` | 구현 완료 |

가장 먼저 추가해야 할 것은 5번 `CloseDailyCandles` Job이다. 그래야 6번 당일 종가
예약 체결이 확정 종가를 안전하게 사용한다. 그다음 Trading의 15:40 체결과 만료를
하나의 순차 흐름으로 구현하고, 마지막으로 Portfolio EOD를 실제 외부 계약과 연결한다.

## 7. 향후 변경사항

1. Batch를 여러 인스턴스로 운영하면 ShedLock 또는 분산 실행 잠금을 도입한다.
2. Stock Service가 시장 목록에서 사라진 종목을 `DELISTED`로 전환하면 Batch 변경
   없이 동일 RPC 결과를 사용한다.
3. 키움 레이트리밋과 연속조회 backoff는 Stock Service에서 보완한다.
4. `SyncStocksResponse.total` 의미가 전체 조회 건수로 분리되면 Batch는 이미
   `upserted`와 `total`을 각각 기록할 수 있다.
5. 전체 시장 단일 호출이 정책으로 확정되면 두 시장 호출을
   `MARKET_TYPE_UNSPECIFIED` 한 번으로 바꿀 수 있다.
6. 장 운영일 판정이 필요해지면 주말 cron만으로 판단하지 않고 Market의 거래일
   계약을 선행 호출한다.
