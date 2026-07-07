# Trading 예약·주문 Batch 운영 가이드

## 1. 목적과 책임

Trading Batch는 장 운영 시각에 맞춰 Trading Service와 Stock Service의 gRPC를
호출한다. Batch는 실행 시각, 순서, 재시도와 실행 이력만 관리하며 Trading DB를
직접 변경하지 않는다.

```text
TradingJobsScheduler
  -> Spring Batch Job
  -> Tasklet
  -> Trading/Stock gRPC
  -> 소유 서비스의 DB 변경 + Outbox 기록
```

운영 코드에는 Fake 데이터가 없다. 테스트에서는 gRPC client를 Mock으로 교체한다.

## 2. Job과 실행 정책

| 시각(KST) | Job | gRPC | 성공 결과 |
| --- | --- | --- | --- |
| 08:30 | `tradingPreviousCloseJob` | `ProcessPrevCloseReservations` | PREV_CLOSE 예약 처리 |
| 09:00 | `tradingOpenLimitJob` | `ProcessOpenLimitReservations` | OPEN+LIMIT 예약을 CONVERTING으로 전환 |
| 15:30 | `tradingExpirePendingOrdersJob` | `ExpirePendingOrders` | PENDING 주문 취소 |
| 15:30 후 | `tradingFailStaleConvertingJob` | 목록 조회 후 `FailStaleConvertingReservation` | 남은 CONVERTING 예약 FAILED |
| 15:40 | `tradingTodayCloseJob` | Stock `CloseDailyCandles` 후 Trading `ProcessTodayCloseReservations` | 확정 일봉 생성 후 TODAY_CLOSE 예약 처리 |
| 15:40 후 | `tradingExpireReservationsJob` | 목록 조회 후 `ExpireReservation` | 남은 RESERVED 예약 EXPIRED |

15:30과 15:40 흐름은 선행 Job이 `COMPLETED`일 때만 후행 Job을 실행한다. 오전
두 Job은 서로 독립적이다. OPEN+MARKET은 Trading 내부 흐름에서 처리되므로 별도
Batch Job을 만들지 않는다.

예약 처리 결과로 생성되는 `ReservationDue`, `ReservationExecuted`,
`ReservationConverted`, `OrderFilled` 이벤트와 Outbox 트랜잭션은 Trading Service의
책임이다. Batch가 Kafka에 직접 발행하지 않는다.

## 3. 재시도·재시작·중복 방지

| 항목 | 정책 |
| --- | --- |
| 일시적 gRPC 오류 | `INTERNAL`, `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `ABORTED` 최대 3회 |
| 영구 오류 | 즉시 Step과 Job 실패 |
| 건별 예약 처리 | 대상 하나가 최종 실패하면 Job 실패, 다음 재실행에서 서비스 상태를 다시 조회 |
| 이미 상태가 바뀐 예약 | Trading이 `false`를 반환하면 정상 skip |
| Job 식별 기준 | `jobName + businessDate` |
| 완료 Job 재호출 | Spring Batch가 `JobInstanceAlreadyCompleteException`으로 중복 실행 차단 |
| 실패 Job 재호출 | 동일 `businessDate`로 재시작 가능 |
| Stock 일봉 마감 | `stock-close-daily:<businessDate>` 결정적 멱등성 키 사용 |

Trading 일괄 RPC는 `RESERVED`, `CONVERTING`, `PENDING` 같은 현재 상태를 조건으로
변경한다. 따라서 재호출 시 이미 처리된 행을 다시 변경하지 않는 계약을 전제로 한다.
이 계약이 바뀌면 Trading 요청에도 명시적인 `CommandMetadata` 멱등성 계약을 추가해야
한다.

현재 분산 실행 잠금은 없다. Batch는 단일 인스턴스 운영을 전제로 하며 다중 인스턴스
배포 전에는 ShedLock 또는 플랫폼 단일 스케줄 실행 정책이 필요하다.

## 4. 운영 설정

```bash
BATCH_TRADING_ENABLED=true
BATCH_TRADING_GRPC_TARGET=static://trading-service:50054
BATCH_STOCK_GRPC_TARGET=static://stock-service:50060
BATCH_TRADING_JOB_DEADLINE_MILLIS=120000

BATCH_TRADING_PREVIOUS_CLOSE_CRON="0 30 8 * * MON-FRI"
BATCH_TRADING_OPEN_LIMIT_CRON="0 0 9 * * MON-FRI"
BATCH_TRADING_MARKET_CLOSE_CRON="0 30 15 * * MON-FRI"
BATCH_TRADING_TODAY_CLOSE_CRON="0 40 15 * * MON-FRI"
```

기본값은 `BATCH_TRADING_ENABLED=false`다. Trading은 `50054`, Stock은 `50060`,
Batch Control은 `50062`를 사용한다. cron의 기준 시간대는 `Asia/Seoul`이다.

주말은 cron으로 제외하지만 공휴일·임시 휴장일은 아직 판별하지 않는다. 거래일
캘린더 계약이 생기면 Scheduler가 Job 시작 전에 영업일 여부를 확인하도록 변경한다.

## 5. 자동 테스트

Trading Batch 전용 테스트:

```bash
./gradlew :batch:test --tests '*trading*'
```

전체 Batch 회귀 테스트:

```bash
./gradlew :batch:test
```

검증 범위는 다음과 같다.

- Trading/Stock proto 요청과 응답 매핑
- 일시적 오류 재시도와 영구 오류 즉시 실패
- 오전 예약 Job 분리
- 15:30·15:40 선후 실행과 선행 실패 시 후행 중단
- 상태가 이미 변경된 예약 skip
- 6개 Job의 Batch Control 노출
- 다른 Batch Job과 설정 호환성

통합 테스트의 gRPC 서버는 `spring.grpc.server.port=0`으로 빈 임시 포트를 사용한다.
운영 포트 `50062`에는 영향을 주지 않는다.

## 6. 로컬 실제 연동 테스트

1. 기반 인프라를 실행한다.

```bash
docker compose up -d postgres redpanda
```

2. Trading과 Stock Service를 각각 실행한다.

```bash
TRADING_GRPC_PORT=50054 ./gradlew :services:trading-service:bootRun
```

```bash
STOCK_GRPC_PORT=50060 ./gradlew :services:stock-service:bootRun
```

3. 기존 테스트 예약을 확인한다. Trading 도메인은 `candle` DB의 schema로 분리되어
있다.

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT id, symbol, timing, order_kind, scheduled_date, status
FROM reservation.reservations
ORDER BY created_at DESC
LIMIT 20;
"
```

4. 로컬에서는 cron을 짧게 바꿔 Batch를 실행할 수 있다. 아래 예시는 각 흐름을
매분의 10·20·30·40초에 실행한다.

```bash
BATCH_TRADING_ENABLED=true \
BATCH_TRADING_PREVIOUS_CLOSE_CRON="10 * * * * *" \
BATCH_TRADING_OPEN_LIMIT_CRON="20 * * * * *" \
BATCH_TRADING_MARKET_CLOSE_CRON="30 * * * * *" \
BATCH_TRADING_TODAY_CLOSE_CRON="40 * * * * *" \
BATCH_TRADING_GRPC_TARGET=static://localhost:50054 \
BATCH_STOCK_GRPC_TARGET=static://localhost:50060 \
./gradlew :batch:bootRun
```

대상 예약이 없으면 처리 건수 0과 `COMPLETED`가 정상 결과다. 상태 변경까지 보려면
Trading의 정상 생성 API로 해당 거래일 예약을 먼저 만들어야 한다. FK와 예약 잔고
불변식을 우회하는 임의 SQL INSERT는 사용하지 않는다.

## 7. DB 검증

Spring Batch 실행 이력:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT ji.job_name, je.status, je.exit_code, je.start_time, je.end_time
FROM batch.batch_job_execution je
JOIN batch.batch_job_instance ji
  ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name LIKE 'trading%'
ORDER BY je.job_execution_id DESC
LIMIT 30;
"
```

예약 처리 결과:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT id, symbol, timing, order_kind, scheduled_date, status,
       converted_order_id, updated_at
FROM reservation.reservations
WHERE scheduled_date = CURRENT_DATE
ORDER BY status, id;
"
```

주문 만료 결과:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT id, symbol, order_kind, status, updated_at
FROM order_svc.orders
ORDER BY updated_at DESC
LIMIT 30;
"
```

Trading Outbox 결과:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT event_type, aggregate_id, occurred_at, published_at
FROM reservation.outbox_events
ORDER BY occurred_at DESC
LIMIT 30;

SELECT event_type, aggregate_id, occurred_at, published_at
FROM order_svc.outbox_events
ORDER BY occurred_at DESC
LIMIT 30;
"
```

Stock 일봉 마감 결과:

```bash
docker compose exec postgres \
psql -U candle -d candle_stock -P pager=off -c "
SELECT stock_code, interval, open_time, close, closed
FROM candles
WHERE interval = '1d'
ORDER BY open_time DESC, stock_code
LIMIT 30;
"
```

## 8. Batch Control 수동 실행

Batch Control의 `ListJobs`에 6개 Trading Job이 노출되고, `TriggerJob`은
`businessDate`를 받는다. 상태 변경 호출이므로 `command_metadata.idempotency_key`가
필수다.

수동 실행도 다음 순서를 지킨다.

```text
tradingPreviousCloseJob
tradingOpenLimitJob
tradingExpirePendingOrdersJob
tradingFailStaleConvertingJob
tradingTodayCloseJob
tradingExpireReservationsJob
```

각 요청의 예시는 다음 형태다.

```json
{
  "jobName": "tradingPreviousCloseJob",
  "parameters": {
    "businessDate": "2026-07-06"
  },
  "commandMetadata": {
    "idempotencyKey": "manual-trading-20260706-prev-close"
  }
}
```

같은 날짜의 완료 Job을 다시 실행하면 `ALREADY_EXISTS`가 정상이다. 실패 Job은 같은
`businessDate`로 다시 실행하며 운영 추적을 위해 동일한 idempotency key를 사용한다.

## 9. 장애 확인 순서

1. Batch 메타데이터에서 실패한 Job과 Step을 확인한다.
2. Batch 로그에서 마지막 gRPC code와 재시도 횟수를 확인한다.
3. Trading 또는 Stock Service 상태와 포트를 확인한다.
4. 소유 서비스 DB 상태와 미발행 Outbox를 확인한다.
5. 원인을 복구한 뒤 같은 `businessDate`로 실패 Job부터 재실행한다.
6. 15:30·15:40 후행 Job은 선행 Job이 완료된 것을 확인한 뒤 실행한다.

## 10. 향후 변경 지점

1. 거래일 캘린더 RPC가 생기면 공휴일과 임시 휴장일 실행을 차단한다.
2. Batch 다중 인스턴스 운영 전에 분산 실행 잠금을 추가한다.
3. Trading batch RPC에 `CommandMetadata`가 추가되면 결정적 일별 키를 전달한다.
4. 대상 목록이 대량이 되면 stale/expire 목록 RPC를 cursor pagination으로 변경한다.
5. 서비스별 처리 건수와 실패 건수를 Micrometer metric 및 알림에 연결한다.
6. 장 마감 시간이 변경되면 환경변수 cron만 변경하고 Job 코드는 유지한다.
