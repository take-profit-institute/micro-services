# Portfolio EOD Snapshot Batch

## 1. 목적과 책임

`portfolioEodSnapshotJob`은 KST 거래일 마감 후 활성 보유자의 현금과 확정 종가를
수집해 Portfolio Service에 일별 스냅샷 저장을 요청한다.

```text
Batch
  -> Portfolio ListActiveHolders
  -> Stock GetPreviousClose
  -> Trading GetBalance
  -> Portfolio RecordDailySnapshot
```

Batch는 다른 서비스 DB를 직접 읽거나 쓰지 않는다. 대상 조회와 스냅샷 저장은
Portfolio, 잔고는 Trading, 일봉 종가는 Stock이 소유한다.

## 2. 현재 외부 계약

| 서비스 | RPC | 사용 데이터 | 기본 포트 |
| --- | --- | --- | --- |
| Portfolio | `HoldingService/ListActiveHolders` | `user_id`, `symbol`, `quantity`, `average_price`, cursor | `50055` |
| Trading | `AccountService/GetBalance` | `available_cash`, `reserved_balance` | `50054` |
| Stock | `ChartService/GetPreviousClose` | `prev_close`, `prev_open_time` | `50060` |
| Portfolio | `PortfolioService/RecordDailySnapshot` | 계산된 총자산과 멱등성 키 | `50055` |

조회 RPC에는 `x-role=SYSTEM`, `x-request-id`를 전달한다. 스냅샷 쓰기에는 추가로
`x-user-id`, `x-idempotency-key`를 전달하고 request의 `idempotency_key`와 동일하게
유지한다.

## 3. 계산 정책

```text
stock_value = sum(quantity * closing_price)
cash = available_cash + reserved_balance
total_asset = cash + stock_value
seed_capital = 100,000,000 KRW
```

- 현금 합산은 Trading 계좌의 잔고 불변식에 따른다.
- 입출금 기능 도입 전 모든 계좌의 초기 원금은 1억 원이다.
- `daily_profit`과 `cumulative_return_rate`는 Portfolio Service가 스냅샷 저장 시
  전일 스냅샷과 원금을 기준으로 계산한다.
- 현재 입출금 기능이 없으므로 `daily_profit`은 당일 총자산과 이전 스냅샷 총자산의
  차이다. 입출금 기능 도입 시 순입출금 차감 계약이 필요하다.

## 4. Job 구조와 데이터 흐름

Job은 두 Step으로 구성된다.

### 4.1 `portfolioEodClosingPriceStep`

1. Portfolio 활성 보유자를 `user_id ASC` cursor로 순회한다.
2. 중복 종목 코드를 제거한다.
3. 거래일 다음 날 00:00 KST를 기준 시각으로 Stock `GetPreviousClose`를 호출한다.
4. 반환된 `prev_open_time`의 KST 날짜가 거래일과 같은지 검증한다.
5. 확정 종가를 Batch stage 테이블에 upsert한다.

### 4.2 `portfolioEodSnapshotStep`

1. 활성 보유자를 chunk 단위로 다시 읽는다.
2. Trading에서 사용자 현금을 조회한다.
3. stage 종가로 주식 평가액과 총자산을 계산한다.
4. 거래일과 사용자 ID로 결정적 멱등성 키를 만든다.
5. Portfolio `RecordDailySnapshot`을 호출한다.

기본 chunk는 사용자 100명, 대상 cursor 최대 크기는 500명이다. 설정으로 변경할 수
있다.

## 5. 멱등성·재시작 정책

| 항목 | 정책 |
| --- | --- |
| JobInstance | `jobName + businessDate` |
| 같은 날짜 완료 Job | Spring Batch가 재실행 차단 |
| 실패 Job | 같은 `businessDate`로 실패 지점부터 재시작 |
| reader 복구 | page token, page index, finished를 ExecutionContext에 저장 |
| 종가 stage | `(job_instance_id, symbol)` upsert |
| 스냅샷 | `(user_id, snapshot_date)` unique 및 결정적 idempotency key |
| chunk 중 일부 외부 쓰기 후 실패 | 재시작 시 같은 key로 재호출하여 기존 결과 반환 |

상태 변경 RPC는 즉시 자동 retry하지 않는다. 서버 commit 후 응답 유실 여부를 Batch가
구분할 수 없기 때문이다. Job을 재시작하면 같은 idempotency key로 안전하게 재호출한다.

조회 RPC의 `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `ABORTED`는
최대 3회 재시도한다. 입력 오류와 데이터 검증 오류는 즉시 Job을 실패시킨다.

## 6. 스케줄과 환경변수

기본값은 평일 16:00 KST이며 안전하게 비활성화되어 있다.

```bash
BATCH_PORTFOLIO_EOD_ENABLED=true
BATCH_PORTFOLIO_EOD_CRON="0 0 16 * * MON-FRI"
BATCH_PORTFOLIO_EOD_CHUNK_SIZE=100
BATCH_PORTFOLIO_EOD_SYMBOL_BATCH_SIZE=500

BATCH_PORTFOLIO_GRPC_TARGET=static://portfolio-service:50055
BATCH_TRADING_GRPC_TARGET=static://trading-service:50054
BATCH_STOCK_GRPC_TARGET=static://stock-service:50060
BATCH_GRPC_READ_DEADLINE_MILLIS=300
BATCH_GRPC_WRITE_DEADLINE_MILLIS=1000
```

EOD는 Trading의 15:40 종가 예약 처리와 Stock 일봉 확정이 완료된 뒤 실행해야 한다.
공휴일과 임시 휴장일 판정은 아직 cron에 포함되지 않으며 거래일 캘린더 계약 도입 시
보완한다.

## 7. 자동 테스트

EOD 전체 테스트:

```bash
./gradlew :batch:test --tests '*portfolio.eod*'
```

Batch 전체 회귀 테스트:

```bash
./gradlew :batch:test
```

검증 범위:

- 활성 보유자 cursor와 포지션 매핑
- Trading 현금 합산 및 overflow 검사
- Stock 종가 기준 시각과 거래일 검증
- 1억 원 초기 원금 정책
- 수량·종가·현금 검증 및 자산 계산
- 결정적 멱등성 키
- cursor page 중간 재시작
- 종가 stage upsert
- chunk 쓰기 실패 후 Job 재실행 정책
- 완료된 동일 거래일 Job 중복 실행 차단

통합 테스트 gRPC 서버는 빈 임시 포트를 사용하며 운영 `50062`와 무관하다.

## 8. 로컬 실제 연동 테스트

1. 인프라를 실행한다.

```bash
docker compose up -d postgres redpanda
```

2. 세 서비스를 각각 실행한다.

```bash
PORTFOLIO_GRPC_PORT=50055 ./gradlew :services:portfolio-service:bootRun
TRADING_GRPC_PORT=50054 ./gradlew :services:trading-service:bootRun
STOCK_GRPC_PORT=50060 ./gradlew :services:stock-service:bootRun
```

3. 테스트할 거래일에 활성 보유자, Trading 계좌, 확정 일봉이 존재하는지 확인한다.
직접 SQL INSERT로 서비스 불변식을 우회하지 말고 정상 주문·체결 흐름으로 데이터를
만드는 것을 우선한다.

4. 가까운 시각으로 cron을 바꿔 Batch를 실행한다.

```bash
BATCH_PORTFOLIO_EOD_ENABLED=true \
BATCH_PORTFOLIO_EOD_CRON="0 * * * * *" \
BATCH_PORTFOLIO_GRPC_TARGET=static://localhost:50055 \
BATCH_TRADING_GRPC_TARGET=static://localhost:50054 \
BATCH_STOCK_GRPC_TARGET=static://localhost:50060 \
./gradlew :batch:bootRun
```

대상자가 0명이면 Job `COMPLETED`와 저장 0건이 정상이다. 실제 적재 확인에는 활성
보유자와 해당 거래일의 확정 일봉이 모두 필요하다.

## 9. DB 검증

Batch 실행 이력:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT ji.job_name, je.status, je.exit_code, je.start_time, je.end_time
FROM batch.batch_job_execution je
JOIN batch.batch_job_instance ji
  ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name = 'portfolioEodSnapshotJob'
ORDER BY je.job_execution_id DESC
LIMIT 10;
"
```

Step 처리 건수:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT step_name, status, read_count, write_count, commit_count, rollback_count
FROM batch.batch_step_execution
ORDER BY step_execution_id DESC
LIMIT 10;
"
```

종가 stage:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT business_date, symbol, closing_price, quoted_at
FROM batch.batch_portfolio_eod_closing_prices
ORDER BY business_date DESC, symbol
LIMIT 30;
"
```

Portfolio 저장 결과:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT user_id, snapshot_date, total_asset, stock_value,
       daily_profit, cumulative_return_rate, created_at
FROM portfolio.portfolio_snapshots
ORDER BY snapshot_date DESC, user_id
LIMIT 30;
"
```

원본 데이터 확인:

```bash
docker compose exec postgres \
psql -U candle -d candle -P pager=off -c "
SELECT user_id, symbol, quantity, average_price, active
FROM portfolio.portfolio_holdings
WHERE active = TRUE AND quantity > 0
ORDER BY user_id, symbol;

SELECT user_id,
       cash_krw - locked_krw AS available_cash,
       locked_krw AS reserved_balance,
       cash_krw AS total_cash
FROM account.accounts
ORDER BY user_id;

SELECT stock_code, open_time, close, closed
FROM stock.candles
WHERE interval = '1d'
ORDER BY open_time DESC, stock_code
LIMIT 30;
"
```

## 10. 실패 확인 순서

1. Batch Job과 Step의 `status`, `exit_message`, rollback count를 확인한다.
2. Batch 로그에서 실패한 서비스와 gRPC code를 확인한다.
3. Portfolio 활성 보유자와 Trading 계좌가 같은 user ID인지 확인한다.
4. Stock 일봉의 `closed=true`, 거래일, 종목 코드를 확인한다.
5. 원인을 복구한 뒤 같은 `businessDate`로 Job을 재실행한다.
6. 이미 저장된 사용자 스냅샷은 같은 멱등성 키로 기존 결과가 반환된다.

## 11. 향후 변경 지점

1. 입출금 기능 도입 시 `FixedSeedCapitalProvider`를 원금·순입출금 조회 계약으로 교체한다.
2. Stock이 종목 묶음 종가 RPC를 제공하면 현재 종목별 `GetPreviousClose` 호출을 bulk
   RPC로 교체한다.
3. 거래일 캘린더 RPC가 생기면 휴장일 실행을 차단한다.
4. Batch 다중 인스턴스 운영 전 분산 실행 잠금을 추가한다.
5. 처리량, 실패 사용자 수, 외부 호출 지연을 metric과 알림에 연결한다.
