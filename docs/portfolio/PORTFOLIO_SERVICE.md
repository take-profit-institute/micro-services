# Portfolio Service

![Portfolio service flow](assets/portfolio-service-flow.svg)

## 1. 역할

`portfolio-service`는 사용자 보유 종목과 포트폴리오 분석용 조회 모델을 관리한다.

주요 책임은 다음과 같다.

| 영역 | 설명 |
| --- | --- |
| Holding | trading-service의 체결 이벤트를 소비해 보유 종목 read model을 갱신 |
| Realized Trade | 매도 체결 시 실현손익 거래 이력을 저장 |
| Portfolio Analytics | 보유 종목, 현재가, 현금 잔고를 조합해 포트폴리오 요약/수익률/섹터 비중 조회 |
| Daily Snapshot | EOD 배치가 넘긴 일별 포트폴리오 스냅샷 저장 및 조회 |

외부 클라이언트가 직접 REST로 호출하지 않고, 내부 gRPC를 통해 호출되는 서비스다.

## 2. 실행 설정

설정 파일: `services/portfolio-service/src/main/resources/application.yml`

| 항목 | 기본값 | 설명 |
| --- | --- | --- |
| HTTP port | `8085` | actuator 등 Spring HTTP 서버 |
| gRPC port | `50055` | `HoldingService`, `PortfolioService` 제공 |
| DB | `jdbc:postgresql://localhost:5432/candle?currentSchema=portfolio,public` | `portfolio` schema 사용 |
| Market gRPC target | `static://localhost:50063` | 현재가 조회 |
| Trading gRPC target | `static://localhost:50054` | 현금 잔고 조회 |
| Kafka bootstrap | `localhost:9092` | trading 체결 이벤트 소비 |

## 3. 패키지 구조

| 패키지 | 역할 |
| --- | --- |
| `holding` | 보유 종목 read model, 체결 이벤트 반영, Holding gRPC |
| `holding.event` | Kafka 체결 이벤트 소비와 중복 소비 방지 |
| `holding.trade` | 실현손익 거래 이력 |
| `analytics` | 포트폴리오 요약, 히스토리, 섹터 비중, 거래 통계 |
| `analytics.market` | market-service gRPC client |
| `analytics.trading` | trading-service gRPC client |
| `config` | 서비스 설정 |

## 4. gRPC API

Proto: `proto/candle/portfolio/v1/portfolio.proto`

### HoldingService

| RPC | 용도 |
| --- | --- |
| `ListHoldings` | 사용자 보유 종목 목록 조회 |
| `GetHolding` | 사용자 단일 보유 종목 조회 |
| `ListActiveHolders` | EOD 배치용 활성 보유자 keyset 조회 |

### PortfolioService

| RPC | 용도 |
| --- | --- |
| `GetPortfolioSummary` | 총 매입금액, 평가금액, 미실현손익, 실현손익, 수익률 조회 |
| `GetPortfolioHistory` | 일별 포트폴리오 스냅샷 히스토리 조회 |
| `GetSectorBreakdown` | 보유 종목의 섹터별 비중 조회 |
| `GetTradingStats` | 실현 거래 기반 승률, 평균 보유일, 최고/최저 손익 조회 |
| `GetMonthlyReturns` | 월별 수익률 조회 |
| `RecordDailySnapshot` | EOD 배치가 일별 스냅샷 저장 |
| `ListDailyPortfolioSnapshots` | ranking 배치가 특정 일자 스냅샷을 페이지 조회 |

## 5. DB 구조

Migration 위치: `services/portfolio-service/src/main/resources/migration`

| 테이블 | 설명 |
| --- | --- |
| `portfolio_holdings` | 사용자별 종목 보유 수량, 평균단가, 매입금액, 실현손익 |
| `portfolio_realized_trades` | 매도 체결로 확정된 실현손익 이력 |
| `portfolio_snapshots` | 일별 포트폴리오 총자산/주식가치/수익률 스냅샷 |
| `portfolio_consumed_events` | Kafka 체결 이벤트 중복 소비 방지 |

현재 DDL은 기본 schema search path가 `portfolio`로 잡힌다는 전제에서 테이블명을 schema 없이 생성한다.

## 6. 체결 이벤트 반영 플로우

구현 진입점: `TradingEventConsumer`

1. Kafka topic `trading.order-filled.v1` 메시지를 수신한다.
2. JSON payload를 `OrderFilledPayload`로 파싱한다.
3. `orderId`를 UUID로 변환해 `portfolio_consumed_events`에서 중복 처리 여부를 확인한다.
4. `side`가 `BUY`이면 `HoldingService.applyBuyFill`을 호출한다.
5. `side`가 `SELL`이면 `HoldingService.applySellFill`을 호출한다.
6. 처리가 성공하면 `ConsumedEvent`를 저장한다.

### BUY 처리

구현: `DefaultHoldingService.applyBuyFill`

| 단계 | 설명 |
| --- | --- |
| 1 | `(userId, symbol)` 보유 종목 조회 |
| 2 | 없으면 새 `HoldingEntity` 생성 |
| 3 | `HoldingEntity.applyBuy(quantity, executedPrice)`로 수량/평균단가/매입금액 갱신 |
| 4 | holding 저장 |

### SELL 처리

구현: `DefaultHoldingService.applySellFill`

| 단계 | 설명 |
| --- | --- |
| 1 | `(userId, symbol)` 보유 종목 조회 |
| 2 | 없으면 `HOLDING_NOT_FOUND` |
| 3 | `HoldingEntity.applySell(quantity, executedPrice)`로 보유 수량과 실현손익 갱신 |
| 4 | holding 저장 |
| 5 | `portfolio_realized_trades`에 실현 거래 저장 |

## 7. 포트폴리오 요약 조회 플로우

구현: `DefaultPortfolioAnalyticsService.getSummary`

1. 활성 보유 종목을 조회한다.
2. 전체 보유 종목을 조회해 누적 실현손익을 계산한다.
3. market-service `BatchQuotes`로 현재가를 조회한다.
4. trading-service `GetBalance`로 현금 잔고를 조회한다.
5. 다음 값을 계산한다.

| 값 | 계산 기준 |
| --- | --- |
| `totalBookValue` | 활성 보유 종목의 `bookValue` 합 |
| `totalStockValue` | `quantity * currentPrice` 합 |
| `totalUnrealizedProfit` | `totalStockValue - totalBookValue` |
| `totalRealizedProfit` | 전체 holding의 `realizedProfit` 합 |
| `totalAsset` | trading 현금 + 주식 평가금액 |
| `dayProfit` | 현재 총자산 - 최신 portfolio snapshot 총자산 |

market-service 조회 실패 시 현재가 map은 비어 있고, 보유 종목의 `cachedCurrentPrice`를 fallback으로 사용한다.
trading-service 잔고 조회 실패 시 현금은 `0`으로 처리한다.

## 8. Daily Snapshot 플로우

구현: `DefaultPortfolioSnapshotService`

### 저장

`RecordDailySnapshot`은 EOD 배치가 호출한다.

1. `(user_id, snapshot_date)` 기존 스냅샷을 조회한다.
2. 있으면 기존 값을 그대로 반환한다.
3. 없으면 이전 스냅샷을 조회해 `dailyProfit`을 계산한다.
4. `seedCapital` 기준으로 `cumulativeReturnRate`를 계산한다.
5. unique 제약 충돌이 발생하면 기존 스냅샷을 다시 조회해 반환한다.

현재 멱등성은 별도 idempotency table이 아니라 `(user_id, snapshot_date)` unique 제약으로 처리한다.

### 조회

`ListDailyPortfolioSnapshots`는 ranking 배치가 특정 일자의 스냅샷을 `user_id` 기준 keyset pagination으로 조회한다.

## 9. 외부 연동

| 대상 | 방식 | 사용 위치 | 실패 처리 |
| --- | --- | --- | --- |
| trading-service | gRPC `AccountService.GetBalance` | 포트폴리오 요약의 현금 조회 | 실패 시 `0` 반환 |
| market-service | gRPC `MarketService.BatchQuotes` | 평가금액/섹터 비중 계산 | 실패 시 빈 map 반환 |
| Kafka | `trading.order-filled.v1` consume | holding read model 갱신 | 처리 실패 시 예외 재던짐 |

## 10. 테스트

주요 테스트 위치:

| 테스트 | 검증 내용 |
| --- | --- |
| `DefaultHoldingServiceTest` | 매수/매도 체결 반영 |
| `TradingEventConsumerTest` | Kafka payload 파싱, 중복 이벤트, BUY/SELL 분기 |
| `HoldingGrpcServiceTest` | HoldingService gRPC mapping |
| `DefaultPortfolioAnalyticsServiceTest` | 포트폴리오 요약/히스토리/섹터/통계 계산 |
| `DefaultPortfolioSnapshotServiceTest` | 스냅샷 저장 멱등성, 페이지 조회 |
| `PortfolioGrpcServiceTest` | PortfolioService gRPC mapping |

## 11. 현재 구조상 주의점

| 항목 | 내용 |
| --- | --- |
| 체결 이벤트 멱등성 | 현재 `orderId`를 dedup key로 사용한다. 동일 주문에 부분 체결 이벤트가 여러 번 발생하는 계약이면 손실 가능성이 있다. |
| Snapshot 멱등성 | 별도 idempotency table이 아니라 `(user_id, snapshot_date)` unique 기반이다. |
| 외부 가격 실패 | market-service 실패 시 `cachedCurrentPrice` fallback을 사용한다. 캐시가 오래되면 평가금액이 실제와 다를 수 있다. |
| 현금 잔고 실패 | trading-service 실패 시 현금 `0`으로 계산되어 총자산이 낮게 보일 수 있다. |
| REST 없음 | BFF를 통해 gRPC로 호출되는 내부 서비스다. |
