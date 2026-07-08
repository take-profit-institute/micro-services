# Stock Service

![Stock service flow](assets/stock-service-flow.svg)

## 1. 역할

`stock-service`는 종목 마스터, 종목 재무정보, 캔들 차트 데이터를 관리한다.

주요 책임은 다음과 같다.

| 영역 | 설명 |
| --- | --- |
| Catalog | 종목 검색, 상세 조회, 일괄 조회, 종목 동기화 |
| Financials | 종목별 최신 재무정보 조회 |
| Chart | 일/주/월 캔들 조회, sparkline, 이전 종가, 가격 통계 |
| Backfill | DB에 캔들이 부족하면 Kiwoom API에서 받아 저장 |
| Daily Close | 일봉을 확정하고 outbox 이벤트를 기록 |
| Outbox | `stock.daily-closed.v1` 이벤트를 Kafka로 발행 |

market-service가 실시간 시세를 담당하고, stock-service는 종목 기준정보와 저장된 캔들 데이터를 담당한다.

## 2. 실행 설정

설정 파일: `services/stock-service/src/main/resources/application.yml`

| 항목 | 기본값 | 설명 |
| --- | --- | --- |
| HTTP port | `8091` | actuator 등 Spring HTTP 서버 |
| gRPC port | `55060` | `StockService`, `ChartService` 제공 |
| DB | `jdbc:postgresql://localhost:5432/candle?currentSchema=stock,public` | `stock` schema 사용 |
| Kafka bootstrap | `localhost:9092` | outbox 이벤트 발행 |
| Outbox publish interval | `5000ms` | 미발행 outbox polling 주기 |
| Kiwoom base URL | `https://api.kiwoom.com` | 종목/차트 fallback API |
| Kiwoom staleness | `P7D` | 종목 상세 fallback 판단 기준 |

## 3. 패키지 구조

| 패키지 | 역할 |
| --- | --- |
| `catalog` | 종목 마스터 검색/조회/동기화 |
| `chart` | 캔들 조회, backfill, 일봉 확정 |
| `client` | Kiwoom HTTP client |
| `event` | outbox 이벤트 저장 및 Kafka 발행 |
| `config` | Kiwoom 설정 등 서비스 설정 |

## 4. gRPC API

Proto:

- `proto/candle/stock/v1/stock.proto`
- `proto/candle/stock/v1/chart.proto`

### StockService

| RPC | 용도 |
| --- | --- |
| `SearchStocks` | 종목 목록/검색 조회 |
| `GetStock` | 종목 상세 조회 |
| `BatchGetStocks` | 여러 종목 기본정보 일괄 조회 |
| `SyncStocks` | Kiwoom에서 시장별 종목을 조회해 DB upsert |

### ChartService

| RPC | 용도 |
| --- | --- |
| `GetCandles` | 특정 종목의 캔들 목록 조회 |
| `GetSparklines` | 여러 종목의 최근 종가 배열 조회 |
| `GetPreviousClose` | 기준 시각 이전의 최신 일봉 종가 조회 |
| `GetPriceStats` | 52주 고가/저가 등 가격 통계 조회 |
| `BackfillCandles` | Kiwoom에서 캔들을 받아 DB upsert |
| `CloseDailyCandles` | 특정 거래일 일봉을 확정하고 outbox 이벤트 기록 |

## 5. DB 구조

Migration 위치: `services/stock-service/src/main/resources/migration`

| 테이블 | 설명 |
| --- | --- |
| `stocks` | 종목 마스터. `stock_code` unique |
| `stock_financials` | 종목별 재무정보. `(stock_id, fiscal_period)` PK |
| `candles` | 캔들 데이터. `(stock_code, interval, open_time)` PK |
| `outbox_events` | Kafka 발행 전 이벤트 저장 |

초기 종목 데이터는 `R__seed_stocks.sql`, `V20260701_008__seed_stock_reference_snapshot.sql` 등으로 seed된다.

## 6. 종목 검색 플로우

구현 진입점: `StockGrpcService.searchStocks`

1. gRPC request의 `page`, `size`, `sort`를 정규화한다.
2. `ListingStatus`가 unspecified이면 기본값을 `LISTED`로 본다.
3. `StockSearchCriteria`로 변환한다.
4. `DefaultStockCatalogService.search`가 repository 검색을 호출한다.
5. 결과를 proto `Stock`으로 변환해 반환한다.

검색 조건:

| 조건 | 처리 |
| --- | --- |
| `query` | 종목명 부분검색 또는 종목코드 부분검색 |
| `market` | `KOSPI`, `KOSDAQ` |
| `sector` | sector 일치 |
| `status` | 기본 `LISTED` |
| `size` | 기본 20, 최대 100 |

정렬은 기본 `stock_code ASC`이며, `NAME_ASC`, `MARKET_CAP_DESC`를 지원한다.

## 7. 종목 상세 조회 플로우

구현: `DefaultStockCatalogService.getStock`

1. DB에서 `stock_code`로 종목을 조회한다.
2. 종목이 없거나 `syncedAt`이 `kiwoom.staleness`보다 오래되면 stale로 판단한다.
3. `allow_fallback=true`이고 stale이면 Kiwoom API로 조회 후 DB에 저장한다.
4. fallback 성공 시 `source=KIWOOM`으로 반환한다.
5. fallback하지 않거나 실패하면 DB 데이터를 반환한다.
6. DB에도 종목이 없으면 `STOCK_NOT_FOUND`를 반환한다.

news-service는 `GetStock(allow_fallback=false)`로 호출하므로 외부 Kiwoom fallback 없이 DB 기준으로만 조회한다.

## 8. 종목 동기화 플로우

구현: `DefaultStockIngestionService.syncMarket`

1. Kiwoom client에서 시장별 전체 종목을 조회한다.
2. 각 종목을 기존 `stocks` row와 매칭한다.
3. 없으면 새 `StockEntity`를 생성한다.
4. 있으면 기준정보를 갱신한다.
5. 저장 중 unique 충돌이 나면 기존 row를 다시 읽어 재저장한다.

`SyncStocks` gRPC는 이 흐름을 호출하고, `upserted`와 `total`을 반환한다.

## 9. 캔들 조회 플로우

구현: `DefaultChartService.getCandles`

1. `code`, `interval`을 검증한다.
2. `limit`을 기본 100, 최대 500으로 정규화한다.
3. DB에서 최신 캔들을 조회한다.
4. DB row 수가 요청 limit보다 적으면 Kiwoom backfill을 실행한다.
5. backfill 후 다시 DB를 조회한다.
6. 그래도 데이터가 없으면 `CHART_DATA_UNAVAILABLE`을 반환한다.
7. 결과는 오래된 순서에서 최신 순서로 정렬해 반환한다.

현재 gRPC layer에서 지원하는 interval은 `DAY_1`, `WEEK_1`, `MONTH_1`이다.

## 10. Backfill 구조

구현:

- `DefaultCandleBackfillService`
- `SingleFlightCandleBackfillService`
- `DefaultCandleBackfillPersistence`

동작:

1. chart 조회 중 DB 데이터가 부족하면 `CandleBackfillService.backfill`을 호출한다.
2. `SingleFlightCandleBackfillService`가 같은 `(code, interval, to)` 요청을 하나로 합친다.
3. 실제 요청은 `DefaultCandleBackfillService`가 Kiwoom chart client를 호출한다.
4. 받아온 캔들은 persistence layer에서 upsert한다.

이 구조로 동일 종목/구간에 대한 동시 backfill 중복 호출을 줄인다.

## 11. Sparkline / Previous Close / Price Stats

| 기능 | 구현 | 설명 |
| --- | --- | --- |
| `GetSparklines` | `DefaultChartService.getSparklines` | 여러 종목의 최근 종가 N개를 DB에서 한 번에 조회 |
| `GetPreviousClose` | `DefaultChartService.getPreviousClose` | 기준 시각 이전 최신 일봉 조회, 없으면 backfill 후 재조회 |
| `GetPriceStats` | `DefaultChartService.getPriceStats` | 최근 일봉 기준 고가/저가/최신 종가/거래량 조회 |

`GetSparklines`는 DB에 없는 종목을 결과에서 생략한다.
`GetPriceStats`는 캔들 자체가 없으면 empty result를 반환한다.

## 12. Daily Close / Outbox 플로우

구현:

- `DefaultDailyCloseService`
- `OutboxWriter`
- `KafkaOutboxPublisher`

동작:

1. EOD 배치가 `CloseDailyCandles(trade_date)`를 호출한다.
2. 해당 거래일 UTC 시작 시각의 `DAY_1` 미확정 캔들을 조회한다.
3. 각 캔들을 `closed=true`로 변경한다.
4. 같은 transaction에서 `outbox_events`에 `StockDailyClosedEvent`를 저장한다.
5. `KafkaOutboxPublisher`가 주기적으로 미발행 outbox를 조회한다.
6. Kafka topic으로 발행한 뒤 `published_at`을 기록한다.

현재 지원 이벤트:

| 이벤트 | Kafka topic |
| --- | --- |
| `stock.daily-closed` | `stock.daily-closed.v1` |

## 13. 외부 연동

| 대상 | 방식 | 사용 위치 |
| --- | --- | --- |
| Kiwoom stock API | HTTP client | 종목 상세 fallback, 시장별 종목 sync |
| Kiwoom chart API | HTTP client | 캔들 backfill |
| Kafka | producer | outbox 이벤트 발행 |

## 14. 테스트

주요 테스트 위치:

| 테스트 | 검증 내용 |
| --- | --- |
| `StockGrpcServiceTest` | StockService gRPC mapping, 검색/상세/동기화 |
| `DefaultStockCatalogServiceTest` | DB 조회, fallback, stale 판단 |
| `DefaultStockIngestionServiceTest` | Kiwoom upsert, 충돌 복구 |
| `ChartGrpcServiceTest` | ChartService gRPC mapping |
| `DefaultChartServiceTest` | 캔들 조회, sparkline, previous close, stats |
| `DefaultCandleBackfillServiceTest` | Kiwoom backfill |
| `SingleFlightCandleBackfillServiceTest` | 동시 backfill 단일화 |
| `DefaultDailyCloseServiceTest` | 일봉 확정과 outbox 기록 |

## 15. 현재 구조상 주의점

| 항목 | 내용 |
| --- | --- |
| gRPC 포트 | 현재 기본값은 `55060`이다. 다른 서비스의 stock target도 이 값과 맞아야 한다. |
| SearchStocks size | 서버에서 최대 100으로 제한한다. 전체 LISTED 종목 조회는 page 단위 반복이 필요하다. |
| fallback | `GetStock(allow_fallback=true)`일 때만 Kiwoom 조회를 수행한다. |
| chart interval | proto에는 분봉 enum이 있지만 gRPC 구현은 현재 일/주/월만 지원한다. |
| outbox 발행 | Kafka 발행 실패 시 `published_at`이 갱신되지 않아 다음 주기에 재시도된다. |
| market-service와 역할 분리 | 실시간 현재가는 stock-service가 아니라 market-service 책임이다. |
