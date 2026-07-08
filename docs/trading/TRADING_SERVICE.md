# Trading Service 구조 설명

이 문서는 `trading-service`의 구조와 동작 방식을 설명한다.
개발 경험이나 gRPC 지식 없이도 읽을 수 있도록 작성했다.

> 이 문서는 이전 온보딩용 초안을 실제 dev 코드 기준으로 갱신한 버전이다. 초안 작성 시점에는 없었던 **예약 주문(Reservation) 도메인**과 **체결(Execution) 처리**가 이번 갱신에서 새로 추가됐다.

---

## 목차

1. [Trading Service가 하는 일](#1-trading-service가-하는-일)
2. [gRPC란 무엇인가](#2-grpc란-무엇인가)
3. [서비스 구조 한눈에 보기](#3-서비스-구조-한눈에-보기)
4. [계층별 역할 설명](#4-계층별-역할-설명)
5. [주요 기능 흐름](#5-주요-기능-흐름)
6. [멱등성이란 무엇이고 왜 필요한가](#6-멱등성이란-무엇이고-왜-필요한가)
7. [Outbox 패턴이란 무엇이고 왜 필요한가](#7-outbox-패턴이란-무엇이고-왜-필요한가)
8. [데이터베이스 테이블 설명](#8-데이터베이스-테이블-설명)
9. [API 계약 (Proto)](#9-api-계약-proto)
10. [핵심 설계 결정 요약](#10-핵심-설계-결정-요약)
11. [테스트는 어떻게 검증하나](#11-테스트는-어떻게-검증하나)

---

## 1. Trading Service가 하는 일

Trading Service는 사용자의 **주식 매매 주문**을 처리하는 서비스다. 내부적으로 **계좌(Account)**, **즉시 주문(Order)**, **예약 주문(Reservation)** 이렇게 세 가지 하위 도메인으로 나뉘어 있고, 각자 다른 DB 스키마(`account` / `order_svc` / `reservation`)를 쓰지만 하나의 서비스로 배포된다.

| 도메인 | 기능 | 설명 |
|------|------|------|
| 계좌 | 잔고 조회 | 사용자의 보유 현금, 잠금 금액, 가용 금액을 반환한다 |
| 즉시 주문 | 주문 생성 | 시장가/지정가 매수·매도 주문을 생성하고, 매수 시 필요한 금액을 잠근다 |
| 즉시 주문 | 주문 체결 | 시장가는 즉시, 지정가는 조건(현재가) 충족 시 체결하고 수수료·거래세를 계산한다 |
| 즉시 주문 | 주문 취소/정정 | 대기 중인 주문을 취소하고 잠긴 금액을 돌려주거나, 취소 후 새 조건으로 재생성한다 |
| 즉시 주문 | 주문 목록 조회 | 사용자의 주문 내역(체결 정보 포함)을 조회한다 |
| 예약 주문 | 예약 생성 | "다음 개장 시가에", "오늘/전일 종가로" 등 미래 시점 체결을 예약한다 |
| 예약 주문 | 예약 취소/정정 | 배치 마감 시간 전이라면 예약을 취소하거나 다시 생성한다 |
| 예약 주문 | 예약 체결(배치) | 정해진 시각에 batch-service가 호출하면 예약을 실제로 체결하거나, 즉시 주문으로 전환한다 |

이전 초안에서 "미구현"이라고 적었던 예약 주문 기능은 **이번 갱신 시점 기준 전부 구현되어 있다.**

---

## 2. gRPC란 무엇인가

### REST API와의 비교

일반적인 웹 서비스는 **REST API**를 사용한다. 예를 들어 주문을 생성할 때 HTTP로 JSON을 주고받는다.

```
POST /orders
{ "symbol": "005930", "side": "BUY", "quantity": 10, "price": 70000 }
```

**gRPC**는 서비스 간 통신을 위해 설계된 다른 방식이다. JSON 대신 **Protocol Buffers(proto)** 라는 이진 형식을 사용한다.

### gRPC를 쓰는 이유

- **성능**: JSON보다 데이터 크기가 작고 직렬화/역직렬화가 빠르다
- **계약이 명확하다**: `.proto` 파일이 API의 유일한 정의다. 스키마가 어긋나면 컴파일 단계에서 오류가 난다
- **마이크로서비스에 적합하다**: 서비스 간 통신에 특화되어 있으며, 서버와 클라이언트 코드를 자동으로 생성한다

### proto 파일이란

`.proto` 파일은 API의 형태를 정의하는 설계도다. 이 파일 하나로 Java 서버 코드와 클라이언트 코드가 자동 생성된다.

> **갱신 사항**: 이전에는 `trading.proto` 파일 하나에 계좌·주문 기능이 모두 들어있었지만, 지금은 도메인별로 4개 파일로 나뉘어 있다(breaking change로 확정 반영됨).
> - `account.proto` — `AccountService` (계좌)
> - `order.proto` — `OrderService` (즉시 주문)
> - `reservation.proto` — `ReservationService` (예약 주문)
> - `trading_common.proto` — 세 서비스가 공유하는 enum(`OrderSide`, `OrderKind`, `OrderStatus`, `ReservationTiming`, `ReservationStatus`)

```protobuf
// proto/candle/trading/v1/order.proto (일부)
service OrderService {
  rpc ListOrders(ListOrdersRequest) returns (ListOrdersResponse);
  rpc PlaceOrder(PlaceOrderRequest) returns (PlaceOrderResponse);
  rpc CancelOrder(CancelOrderRequest) returns (CancelOrderResponse);
  rpc AmendOrder(AmendOrderRequest) returns (AmendOrderResponse);
  rpc ExpirePendingOrders(ExpirePendingOrdersRequest) returns (ExpirePendingOrdersResponse); // 배치 전용
}
```

위 정의는 "OrderService라는 gRPC 서비스가 있고, 주문 조회/생성/취소/정정/배치 만료 기능을 제공한다"는 계약이다.

### gRPC 요청 흐름

```
클라이언트 앱(BFF)
    │
    │  gRPC 호출 (이진 데이터)
    ▼
[IdempotencyServerInterceptor] ← 모든 요청이 먼저 거치는 관문
    │          (x-user-id, x-idempotency-key 추출 → IdempotencyContext 생성)
    ▼
[각 도메인의 *GrpcService] ← 요청 파싱, actor 검증, 응답 변환
    │
    ▼
[각 도메인의 *Service / *ExecutionService / *BatchService] ← 실제 비즈니스 로직
    │
    ▼
[PostgreSQL — account / order_svc / reservation 스키마]
```

---

## 3. 서비스 구조 한눈에 보기

> **갱신 사항**: 이전 초안은 계좌+주문 기능이 `TradingGrpcService`/`TradingDomainService` 하나로 뭉쳐 있는 구조를 가정했지만, 실제로는 **도메인별 패키지 분리**(layer 기준이 아니라 `account`/`order`/`reservation`/`support`/`client` 도메인 기준) 구조다. 아래는 실제 패키지 구조를 반영한 트리다.

```
trading-service/src/main/java/org/profit/candle/trading/
│
├── account/
│   ├── entity/        AccountEntity, AccountStatusValue, ConsumedEvent
│   ├── service/        AccountService, DefaultAccountService
│   ├── grpc/           AccountGrpcService            ← AccountService gRPC 진입점
│   ├── repository/     AccountRepository, AccountOutboxEventRepository,
│   │                    AccountIdempotencyRecordRepository, ConsumedEventRepository
│   ├── event/           OutboxEvent(AccountOutboxEvent), IdempotencyRecord(AccountIdempotencyRecord),
│   │                    IdempotencyRecordId, UserCreatedPayload
│   ├── event/AccountUserCreatedEventConsumer.java   ← auth.user-created.v1 Kafka 구독
│   └── exception/      AccountErrorCode, AccountException
│
├── order/
│   ├── entity/          OrderEntity, ExecutionEntity, OrderSideValue, OrderKindValue, OrderStatusValue
│   ├── dto/              PlaceOrderCommand, AmendOrderCommand, CancelResult
│   ├── service/
│   │   ├── OrderService, DefaultOrderService                  ← 접수/취소/정정
│   │   ├── OrderExecutionService, DefaultOrderExecutionService ← 체결(시장가/지정가)
│   │   ├── OrderLimitFillExecutor                              ← 지정가 체결 건별 트랜잭션 단위
│   │   ├── CachedMarketPriceProvider, MarketPriceProvider      ← 현재가 인메모리 캐시
│   │   └── TradingHoursValidator                                ← 정규장 시간 검증(market-service 위임)
│   ├── grpc/            OrderGrpcService              ← OrderService gRPC 진입점
│   ├── repository/     OrderRepository, ExecutionRepository, OrderOutboxEventRepository,
│   │                    OrderIdempotencyRecordRepository
│   ├── event/           OutboxEvent(OrderOutboxEvent), IdempotencyRecord(OrderIdempotencyRecord),
│   │                    OrderPlacedPayload, OrderFilledPayload, OrderCancelledPayload, OrderAmendedPayload,
│   │                    ReservationConvertedPayload
│   ├── event/OrderMarketPriceConsumer.java          ← 현재가 Kafka 구독(캐시 갱신 + 지정가 체결)
│   ├── event/ReservationDueConsumer.java             ← 예약→주문 전환(시가+지정가) 수신
│   ├── event/ReservationExecutedConsumer.java        ← 예약 확정 체결가 수신 → 주문 생성
│   └── exception/       OrderErrorCode, OrderException
│
├── reservation/
│   ├── entity/           ReservationEntity, ReservationSideValue, ReservationTimingValue,
│   │                     ReservationOrderKindValue, ReservationStatusValue
│   ├── dto/              PlaceReservationCommand, AmendReservationCommand, ReservationCancelResult
│   ├── service/
│   │   ├── ReservationService, DefaultReservationService        ← 접수/취소/정정(사용자 명령)
│   │   ├── ReservationBatchService, DefaultReservationBatchService ← 배치 체결/전환 오케스트레이션
│   │   ├── ReservationBatchExecutor                              ← 건별 트랜잭션 실행 단위
│   │   └── ReservationDeadlineValidator                          ← 예약 접수 마감 시간 검증
│   ├── grpc/             ReservationGrpcService       ← ReservationService gRPC 진입점(배치 RPC 포함)
│   ├── repository/      ReservationRepository, ReservationOutboxEventRepository,
│   │                     ReservationIdempotencyRecordRepository
│   ├── event/            OutboxEvent(ReservationOutboxEvent), IdempotencyRecord(ReservationIdempotencyRecord),
│   │                     ReservationReservedPayload, ReservationCancelledPayload, ReservationDuePayload,
│   │                     ReservationExecutedPayload, ReservationConvertedPayload
│   ├── event/ReservationConvertedConsumer.java       ← order_svc의 전환 완료 알림 수신
│   ├── event/ReservationMarketPriceConsumer.java      ← 현재가 Kafka 구독(OPEN+MARKET 즉시 체결)
│   └── exception/        ReservationErrorCode, ReservationException
│
├── client/               (도메인 간 공유, 외부 서비스 gRPC 클라이언트)
│   ├── MarketSessionClient, DefaultMarketSessionClient  ← market-service (장 상태/거래일)
│   └── ChartServiceClient, DefaultChartServiceClient    ← chart-service (전일/당일 종가 조회)
│
└── support/              (도메인 무관 공통 인프라, Spring 컴포넌트 스캔 범위 안)
    ├── TradingFeePolicy                          ← 수수료·거래세 상수(order/reservation 공유)
    ├── config/  ClockConfig, KafkaConsumerConfig, PayloadEncryptionConfig
    ├── event/    OutboxOperations, OutboxWriter, TradingKafkaOutboxPublisher, TradingOutboxTopics,
    │             MarketPriceEvent
    └── idempotency/  IdempotencyContext, IdempotencyServerInterceptor,
                       IdempotencyExecutor, IdempotencyOperations, RequestHasher
```

암복호화를 담당하는 `EncryptedPayloadConverter`는 `org.profit.candle.common.security` 패키지(공용 `common` 모듈)에 있다 — trading-service 컴포넌트 스캔 범위 밖이라 별도 부팅 절차(4-3절)가 필요하다.

---

## 4. 계층별 역할 설명

### 4-1. gRPC 엔드포인트 (`*/grpc/*GrpcService.java`)

**비유:** 식당의 웨이터. 손님(클라이언트/BFF)의 주문을 받아 주방(도메인 서비스)에 전달하고, 완성된 음식(응답)을 손님에게 내준다. 이 서비스에는 웨이터가 셋 있다 — `AccountGrpcService`, `OrderGrpcService`, `ReservationGrpcService`. 각자 자기 홀(도메인)만 담당한다.

하는 일:
- 들어온 gRPC 메시지를 Java 객체로 변환한다
- 인증된 사용자 ID(`x-user-id`)를 검증한다 — request body의 `user_id`는 이 값과 일치하는지만 확인한다
- 쓰기 요청은 `IdempotencyExecutor`를 통해 도메인 서비스를 호출한다
- 도메인 예외(`OrderException`, `AccountException` 등)를 gRPC `Status`로 변환해 반환한다
- 결과를 gRPC 메시지 형태로 변환해 반환한다

하지 않는 일:
- 비즈니스 규칙을 직접 판단하지 않는다(잔고 충분한지, 거래시간인지 등)
- 목록 조회를 제외하면 리포지토리를 직접 다루지 않는다

```java
// OrderGrpcService.java 중 주문 생성 부분(실제 코드)
@Override
public void placeOrder(PlaceOrderRequest request, StreamObserver<PlaceOrderResponse> observer) {
    try {
        UUID actor = requireActor(request.getUserId());               // 1. 인증 검증
        String idempotencyKey = currentIdempotencyKey();
        var command = new PlaceOrderCommand(
                request.getSymbol(), toSide(request.getSide()), toKind(request.getKind()),
                request.getQuantity(), request.getPrice(), idempotencyKey);  // 2. proto → Java 변환

        PlaceOrderResponse response = idempotencyExecutor.execute(     // 3. 멱등성 처리 후
                request, PlaceOrderResponse.parser(), idempotencyOperations,
                () -> {
                    OrderEntity order = orderService.placeOrder(actor, command); // 도메인 호출
                    ExecutionEntity execution = executionRepository.findByOrderId(order.getId()).orElse(null);
                    return PlaceOrderResponse.newBuilder().setOrder(toProto(order, execution)).build();
                });

        observer.onNext(response);      // 4. 응답 반환
        observer.onCompleted();
    } catch (OrderException e) {
        observer.onError(toGrpcException((OrderErrorCode) e.errorCode()));
    } catch (AccountException e) {
        observer.onError(toGrpcException((AccountErrorCode) e.errorCode()));
    }
}
```

### 4-2. 도메인 서비스 (`*/service/Default*Service.java`)

**비유:** 식당의 주방장. 실제로 요리를 한다. 재료(데이터)를 확인하고, 레시피(비즈니스 규칙)에 따라 처리한다. 이 서비스에는 도메인마다 주방장이 따로 있다.

| 클래스 | 담당 |
| --- | --- |
| `DefaultAccountService` | 잔고 조회/잠금/해제/정산 — 다른 도메인이 잔고를 건드릴 때 이 클래스만 거친다 |
| `DefaultOrderService` | 즉시 주문 접수/취소/정정, 예약 전환 주문 생성 |
| `DefaultOrderExecutionService` + `OrderLimitFillExecutor` | 시장가 즉시 체결, 지정가 조건 체결(수수료·거래세 계산 포함) |
| `DefaultReservationService` | 예약 접수/취소/정정(사용자 명령), 마감 시간·실행일 검증 |
| `DefaultReservationBatchService` + `ReservationBatchExecutor` | 배치 트리거로 예약을 실제 체결/전환 |

```java
// DefaultOrderService.java 중 주문 생성 핵심 로직(실제 코드, 일부 축약)
public OrderEntity placeOrder(UUID userId, PlaceOrderCommand command) {
    tradingHoursValidator.requireMarketOpen();               // 1. 정규장 시간 검증(market-service 위임)

    if (command.quantity() <= 0) throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
    if (command.kind() == OrderKindValue.LIMIT && command.price() <= 0)
        throw new OrderException(OrderErrorCode.INVALID_PRICE);

    AccountEntity account = accountService.getAccount(userId);

    if (orderRepository.existsByAccountIdAndSymbolAndStatus(          // 2. 동일 종목 중복 주문 방지
            account.getId(), command.symbol(), OrderStatusValue.PENDING)) {
        throw new OrderException(OrderErrorCode.DUPLICATE_PENDING_ORDER);
    }

    long reservedAmountKrw = 0;
    if (command.side() == OrderSideValue.BUY && command.kind() == OrderKindValue.LIMIT) {
        long amount = command.price() * command.quantity();
        long fee = BigDecimal.valueOf(amount).multiply(TradingFeePolicy.FEE_RATE)
                .setScale(0, RoundingMode.DOWN).longValue();
        reservedAmountKrw = amount + fee;
        accountService.lockBalance(userId, reservedAmountKrw);        // 3. 잔고 잠금
    }

    OrderEntity order = OrderEntity.place(userId, account.getId(), command.symbol(), command.side(),
            command.kind(), command.quantity(), /* priceKrw */ null, reservedAmountKrw, command.idempotencyKey());
    orderRepository.save(order);                                       // 4. 주문 저장

    outboxWriter.record(outboxOperations, "OrderPlaced", order.getId().toString(), /* payload */ null); // 5. 이벤트 기록

    if (command.kind() == OrderKindValue.MARKET) {
        return orderExecutionService.fillMarketOrder(order.getId());  // 시장가는 같은 트랜잭션에서 즉시 체결
    }
    return order;
}
```

> 이전 초안과의 차이: 잔고 검증 실패 시 `Status.FAILED_PRECONDITION`을 직접 던지던 것과 달리, 실제 코드는 도메인 예외(`OrderException`, `AccountException`)를 던지고 gRPC 계층에서 변환한다 — "ErrorCode는 전송 계층(gRPC)을 모른다"는 컨벤션 때문이다.

### 4-3. Entity (`*/entity/*.java`)

**비유:** 데이터베이스 테이블의 Java 표현. 하나의 Entity 객체가 테이블의 한 행(row)에 대응한다.

**중요 설계 원칙(이전 초안과 동일하게 유지됨):**
- `public setter`를 만들지 않는다
- 상태 변경은 의미 있는 메서드로만 한다

```java
// OrderEntity.java (실제 코드)
public void markCancelled() {
    if (!pending()) throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
    if (orderKind != OrderKindValue.LIMIT) throw new OrderException(OrderErrorCode.MARKET_ORDER_CANNOT_BE_CANCELLED);
    this.status = OrderStatusValue.CANCELLED;
    this.updatedAt = Instant.now();
}
```

```java
// AccountEntity.java (실제 코드) — 가용 금액, 잠금/해제/정산
public long availableKrw() { return cashKrw - lockedKrw; }
public void lock(long amount) { /* 검증 후 */ this.lockedKrw += amount; }
public void release(long amount) { /* 검증 후 */ this.lockedKrw -= amount; }
public void settleBuy(long lockedAmount, long settledAmount) {
    if (lockedAmount > 0) release(lockedAmount);
    this.cashKrw -= settledAmount;
}
```

체결 결과는 주문(`OrderEntity`)에 직접 저장하지 않고, **append-only**(생성만 되고 수정 안 됨) 별도 테이블 `ExecutionEntity`에 저장한다 — `order_id`가 UNIQUE라 체결 전엔 row 자체가 없다.

```java
// ExecutionEntity.java (실제 코드)
public static ExecutionEntity create(UUID orderId, long executedPriceKrw, long executedQuantity,
                                     long feeKrw, long taxKrw, long netAmountKrw, Instant executedAt) {
    return new ExecutionEntity(orderId, executedPriceKrw, executedQuantity, feeKrw, taxKrw, netAmountKrw, executedAt);
}
```

예약(`ReservationEntity`)은 상태가 더 다양하다 — `RESERVED → CONVERTING → EXECUTED`(시가+지정가) 또는 `RESERVED → EXECUTED`(그 외), 그리고 `CANCELLED`/`FAILED`/`EXPIRED`.

```java
// ReservationEntity.java (실제 코드, 일부)
public void startConverting() {
    if (!reserved()) throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_RESERVED);
    if (orderKind != ReservationOrderKindValue.LIMIT || timing != ReservationTimingValue.OPEN)
        throw new ReservationException(ReservationErrorCode.NOT_CONVERTIBLE);
    this.status = ReservationStatusValue.CONVERTING;
}
```

### 4-4. self-invocation 문제와 "실행기(Executor)" 클래스들

이전 초안에는 없던 패턴이다. Spring의 `@Transactional`은 프록시로 동작하는데, 같은 클래스 안에서 `this.다른메서드()`를 호출하면 프록시를 우회해서 트랜잭션이 적용되지 않는다. 이를 피하기 위해 배치·체결처럼 "건별로 별도 트랜잭션이 필요한 로직"은 별도 `@Service`로 분리해뒀다.

- `OrderLimitFillExecutor` — 지정가 조건 체결을 건별 트랜잭션으로 실행(`DefaultOrderExecutionService`가 호출)
- `ReservationBatchExecutor` — 예약 배치의 락 획득/상태 전이/잔고 선점을 건별 트랜잭션으로 실행(`DefaultReservationBatchService`가 호출)

### 4-5. 외부 서비스 클라이언트 (`client/`)

**비유:** 다른 식당(서비스)에 전화로 재료 상태를 확인하는 역할.

- `MarketSessionClient` → market-service: "지금 장이 열려 있나?"(`GetMarketStatus`, 3초 데드라인), "이 날짜가 거래일인가?"(`IsTradingDay`)
- `ChartServiceClient` → chart-service: "이 종목의 특정 기준일 이전 마지막 종가는?"(`GetPreviousClose`, 5초 데드라인)

두 클라이언트 모두 실패 시 예외를 그대로 전파한다(fail-safe) — 장 상태나 종가를 확인 못 한 채로 주문/예약을 체결하지 않는다.

---

## 5. 주요 기능 흐름

### 5-1. 즉시 주문 생성 흐름 (PlaceOrder, 지정가 매수 예시)

```
클라이언트(BFF)
    │  PlaceOrderRequest {
    │    user_id: "...", symbol: "005930", side: BUY, kind: LIMIT,
    │    quantity: 10, price: 70000,
    │    command_metadata: { idempotency_key: "uuid-xxxx" }
    │  }
    ▼
[IdempotencyServerInterceptor]
    │  x-idempotency-key, x-user-id 헤더 추출 → IdempotencyContext 생성
    ▼
[OrderGrpcService.placeOrder]
    │  actor 검증, proto → PlaceOrderCommand 변환
    ▼
[IdempotencyExecutor.execute]
    │  이 idempotency_key로 처리한 기록이 있는가?
    │  ├─ 있음 (같은 내용) → 저장된 응답을 그대로 반환 (실제 처리 안 함)
    │  ├─ 있음 (다른 내용) → ALREADY_EXISTS 오류 반환
    │  └─ 없음 → 아래 트랜잭션 실행
    ▼
[트랜잭션 시작] ─────────────────────────────────────────
    ├─ DefaultOrderService.placeOrder
    │   ├─ 정규장 시간 검증 (market-service.GetMarketStatus 호출)
    │   ├─ 입력값 검증 (수량·가격 > 0)
    │   ├─ 동일 종목 PENDING 주문 중복 여부 확인
    │   ├─ (매수 지정가면) 잔고 잠금: DefaultAccountService.lockBalance
    │   ├─ 주문 저장 (status=PENDING)
    │   └─ OutboxWriter.record("OrderPlaced", ...)
    └─ IdempotencyRecord 저장 (암호화된 응답 내용 포함)
[트랜잭션 종료] ─────────────────────────────────────────
    ▼
클라이언트에 PlaceOrderResponse 반환
```

시장가 주문이면 같은 트랜잭션 안에서 `DefaultOrderExecutionService.fillMarketOrder`가 이어져 체결까지 끝난다(현재가 조회 → 수수료/거래세 계산 → `ExecutionEntity` 저장 → 잔고 정산 → `OrderFilled` outbox 기록).

### 5-2. 지정가 조건 체결 흐름 (완전 비동기, 이전 초안엔 없던 흐름)

```
Market 서비스가 현재가 Kafka 이벤트 발행(market.order-book.v1)
    ▼
[OrderMarketPriceConsumer.consume]
    ├─ 1. CachedMarketPriceProvider.updatePrice   (인메모리 캐시 갱신)
    └─ 2. DefaultOrderExecutionService.fillLimitOrdersIfConditionMet
            ├─ 락 없이 후보 조회 (symbol + PENDING + LIMIT)
            └─ 건별로 OrderLimitFillExecutor.fillIfConditionMet
                    ├─ 락 획득 → 가격 조건 재검증
                    ├─ ExecutionEntity 저장, 잔고 정산, order.fill()
                    └─ "OrderFilled" outbox 기록
```

### 5-3. 주문 취소 흐름 (CancelOrder)

```
클라이언트
    │  CancelOrderRequest { user_id, order_id, command_metadata }
    ▼
[멱등성 처리 → 트랜잭션]
    ├─ 주문 조회 (없거나 다른 사용자 소유면 ORDER_NOT_FOUND)
    ├─ 주문 상태 확인 (PENDING·LIMIT이 아니면 각각 다른 오류)
    ├─ 잠금 금액 해제 (매수만 해당): DefaultAccountService.releaseBalance
    ├─ 주문 상태 변경 (status=CANCELLED)
    └─ OutboxWriter.record("OrderCancelled", ...)
    ▼
CancelOrderResponse { order, released_amount } 반환
```

### 5-4. 주문 정정 흐름 (AmendOrder — 이전 초안엔 없던 흐름)

정정은 별도 "수정" 로직이 아니라 **취소 + 신규 생성**이다.

```
DefaultOrderService.amendOrder
    ├─ 원 주문 취소 (5-3과 동일 + "OrderCancelled" outbox)
    ├─ 새 조건으로 재검증 (수량·가격)
    ├─ (매수면) 새 금액으로 잔고 재잠금
    └─ 신규 주문 생성 — OrderEntity.placeWithParent(..., parentOrderId=원주문ID)
            + "OrderAmended" outbox 기록
```

### 5-5. 잔고 조회 흐름 (GetBalance)

```
클라이언트
    │  GetBalanceRequest { user_id }
    ▼
[AccountGrpcService.getBalance] → actor 검증
    ▼
[DefaultAccountService.getAccount] → 잔고 조회 (없으면 예외 — 자동 생성은 잠금/해제 시점에만 fallback으로 발생)
    ▼
GetBalanceResponse {
  cash: 100_000_000,          // 보유 현금(신규 계좌 시드머니는 1억원)
  reserved_balance: 700_700,  // 잠금 금액
  available_cash: 99_299_300  // 가용 금액
} 반환
```

> 이전 초안은 초기 잔고를 1,000만원, "조회 시 없으면 자동 생성"이라고 설명했지만, 실제 코드는 **시드머니 1억원**이고 자동 생성 fallback은 `getAccount`가 아니라 **잔고 잠금/해제/정산 메서드(`loadOrCreateForUpdate`)에서만** 일어난다. 정상 흐름에서는 회원가입 시 발행되는 `auth.user-created.v1` 이벤트로 계좌가 미리 생성되어 있어야 한다(ACC-001).

### 5-6. 예약 주문 생성 흐름 (PlaceReservation — 신규)

```
클라이언트
    │  PlaceReservationRequest { symbol, side, timing(OPEN/PREV_CLOSE/TODAY_CLOSE),
    │                             kind(MARKET/LIMIT/AFTER_HOURS_CLOSE), quantity, price?, scheduled_date? }
    ▼
[멱등성 처리 → 트랜잭션]
    ├─ 수량 검증
    ├─ 실행 예정일 계산/검증 (전일종가=내일 고정, 시가/당일종가=내일~+7일, 거래일 여부는 market-service에 위임)
    ├─ 오늘 실행 예정이면 접수 마감 시간 검증 (ReservationDeadlineValidator)
    ├─ 동일 종목 RESERVED 예약 중복 방지
    ├─ (매수 + 시가&지정가 조합만) 정확한 금액 계산 가능 → 잔고 잠금
    │   (시장가/시간외종가는 체결 시점 가격을 몰라 배치 체결 시점에 잠금)
    ├─ 예약 저장 (status=RESERVED)
    └─ OutboxWriter.record("ReservationReserved", ...)
```

### 5-7. 예약 → 주문 전환 흐름 (시가+지정가 전용, 신규)

```
[09:00 배치] batch-service → ProcessOpenLimitReservations RPC 호출
    ├─ RESERVED(OPEN+LIMIT) → CONVERTING 전이
    └─ "ReservationDue" outbox 기록
        ▼ (Kafka: trading.reservation.ReservationDue)
    [order 도메인] ReservationDueConsumer
        ├─ DefaultOrderService.placeOrderFromReservation → Order 생성(PENDING, LIMIT)
        └─ "ReservationConverted" outbox 기록 (Order 생성과 같은 트랜잭션)
            ▼ (Kafka: trading.order.ReservationConverted)
        [reservation 도메인] ReservationConvertedConsumer
            └─ CONVERTING → EXECUTED 전이 (converted_order_id 기록)
```

**동기 호출이 전혀 없다** — reservation과 order 두 도메인은 Kafka로만 대화한다(Option C: 전체 비동기).

### 5-8. 예약 즉시 체결 흐름 (전일/당일종가, 시가+시장가 — 신규)

```
[배치 또는 현재가 이벤트] → 락 획득 → RESERVED 확인 → 즉시 커밋(락 보유시간 최소화)
    ├─ (전일/당일종가) chart-service.GetPreviousClose로 확정 종가 조회
    ├─ (매수면) 계산된 금액만큼 잔고 선점(REQUIRES_NEW 트랜잭션)
    ├─ 락 재획득 → EXECUTED 전이 + "ReservationExecuted" outbox 기록
    └─ (실패 시) 잔고 보상(release)
        ▼ (Kafka: trading.reservation.ReservationExecuted)
    [order 도메인] ReservationExecutedConsumer
        └─ DefaultOrderService.recordReservationFill
                → 확정 체결가로 MARKET 주문 생성 + 같은 트랜잭션에서 즉시 체결
                → "OrderPlaced"/"OrderFilled" outbox 기록
```

이 경로를 거쳐야 시장가/종가 예약 체결이 실제 주문·체결 레코드와 `OrderFilled`(portfolio 반영)까지 이어진다.

---

## 6. 멱등성이란 무엇이고 왜 필요한가

*(이 장의 개념 설명은 이전 초안과 동일 — 여전히 정확하다. 실제 구현과 다른 세부사항만 갱신했다.)*

### 문제 상황

주문 생성 요청을 보냈는데 네트워크 오류로 응답을 받지 못했다. 주문이 처리됐는지 알 수 없다.
다시 요청을 보내면 주문이 두 번 생성될 수 있다.

### 멱등성(Idempotency)의 정의

**같은 요청을 여러 번 보내도 결과가 한 번 보낸 것과 동일하게 보장하는 성질이다.**

전원 버튼을 예로 들면, 이미 켜진 전원 버튼을 한 번 더 눌러도 켜진 상태는 변하지 않는다. 주문도 마찬가지로 같은 요청은 항상 같은 결과를 반환해야 한다.

### 구현 방식

```
클라이언트가 요청 시 고유한 UUID를 생성해 함께 보낸다
(x-idempotency-key 헤더 + command_metadata.idempotency_key 필드)

서버는 이 키를 DB에 기록한다
같은 키로 다시 요청이 오면 실제 처리 없이 저장된 결과를 그대로 반환한다
```

### 핵심 컴포넌트

**1. `IdempotencyServerInterceptor`** — 모든 gRPC 요청의 관문. `x-idempotency-key`가 오면 형식(UUID, 64자 이하)만 검증하고, `x-user-id`와 함께 `IdempotencyContext`를 만들어 gRPC 컨텍스트에 실어 전달한다. 읽기 RPC에는 키가 없을 수 있어 여기서 누락을 강제하지 않는다.

**2. `RequestHasher`** — 요청 내용의 지문 계산. `command_metadata.idempotency_key` 필드만 지운 사본을 결정론적으로 직렬화한 뒤 `v1\n<gRPC full method name>\n<actor id>\n<직렬화 바이트>`를 SHA-256으로 해시한다. 같은 키를 재사용하면서 다른 내용을 보내는 요청을 감지한다.

**3. `IdempotencyExecutor`** — 핵심 처리 알고리즘. 도메인마다 `idempotency_records` 테이블이 스키마별로 따로 있어서, 이 클래스는 어떤 리포지토리를 쓸지 모른다 — 호출하는 쪽(각 `*GrpcService`)이 자기 도메인의 `IdempotencyOperations` 구현체를 넘겨준다(3개 도메인이 이 실행기 하나를 공유).

```
멱등성 키로 기존 레코드 조회
    ├─ 없음 → 새로 처리 (도메인 로직 실행 + 결과 저장)
    ├─ 있음 + 같은 내용 → 저장된 응답 그대로 반환 (재처리 없음)
    └─ 있음 + 다른 내용 → ALREADY_EXISTS 오류 (키 재사용 거부)
```

**동시 요청 처리:** 두 요청이 동시에 도착해 둘 다 "기록 없음"으로 판단하면 DB의 복합 PK(`actor_id, operation, idempotency_key`) 충돌이 감지한다. 나중에 도착한 요청은 먼저 처리된 결과를 재조회해 반환한다.

### 처리 보장 범위

도메인 변경 + Outbox 이벤트 기록 + 멱등성 레코드 저장이 **하나의 트랜잭션**으로 묶인다.

```java
// IdempotencyExecutor.java (실제 코드, 일부)
return transactionTemplate.execute(status -> {
    R response = command.get();     // 도메인 변경 + outbox 기록
    ops.save(ops.newRecord(id, requestHash, response.toByteArray(),
            response.getDescriptorForType().getFullName(), Status.Code.OK.name(),
            Instant.now().plus(defaultTtl)));
    return response;
});
```

### 갱신 사항 — 응답 저장값 암호화

이전 초안에는 없던 내용이다. `idempotency_records.response_payload`는 저장 전 **AES-256-GCM으로 암호화**된다(`EncryptedPayloadConverter`, JPA `@Convert`). 3개 도메인(`AccountIdempotencyRecord`/`OrderIdempotencyRecord`/`ReservationIdempotencyRecord`) 모두 동일하게 적용돼 있다. 암호화 키는 서비스 부팅 시 `PayloadEncryptionConfig`가 `candle.security.payload-encryption-key` 설정값으로 주입한다 — DB에 사용자 응답이 그대로 평문 저장되지 않는다는 뜻이다.

---

## 7. Outbox 패턴이란 무엇이고 왜 필요한가

*(개념 설명은 이전 초안과 동일 — 여전히 정확하다. "별도 발행자"의 실제 구현 방식만 갱신했다.)*

### 문제 상황

주문/예약 상태가 바뀌었을 때 다른 서비스(portfolio 등)나 같은 서비스의 다른 도메인(order↔reservation)에도 그 사실을 알려야 한다. 가장 단순한 방법은 DB 저장 후 Kafka에 메시지를 보내는 것이지만, 이 방식은 위험하다.

```
❌ 잘못된 방식
    ├─ DB에 상태 저장 ← 성공
    └─ Kafka에 메시지 전송 ← 실패!
         → DB에는 반영됐지만 다른 서비스는 모른다 (데이터 불일치)
```

### Outbox 패턴의 해결책

DB에 이벤트를 직접 기록하고, **별도의 발행자**가 나중에 Kafka로 전송한다.

```
✅ Outbox 패턴

[하나의 트랜잭션]
    ├─ DB에 도메인 상태 저장
    └─ DB의 outbox_events 테이블에 이벤트 기록
    → 둘 다 성공하거나 둘 다 실패 (트랜잭션으로 보장)

[별도 발행자 — 스케줄러로 주기 실행]
    ├─ outbox_events에서 미발행 이벤트 조회
    ├─ Kafka로 전송
    └─ 발행 완료 처리 (published_at 기록)
```

### 갱신 사항 — 도메인마다 outbox 테이블이 따로 있고, 발행자는 3개를 동시에 처리한다

이전 초안은 `outbox_events` 테이블 하나를 가정했지만, 실제로는 **스키마별로 3개**(`account.outbox_events`, `order_svc.outbox_events`, `reservation.outbox_events`)가 있다. 아직 CDC(Debezium)가 없어서, `TradingKafkaOutboxPublisher`라는 스케줄러(기본 2초 주기)가 3개 테이블을 순서대로 폴링해 Kafka로 직접 발행하는 **임시 릴레이** 역할을 한다.

```java
// TradingKafkaOutboxPublisher.java (실제 코드, 일부)
@Scheduled(fixedDelayString = "${trading.outbox.publish-interval-ms:2000}")
@Transactional
public void publishPendingEvents() {
    orderOutboxRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(e -> {
        kafkaTemplate.send(TradingOutboxTopics.forOrderEvent(e.eventType()), e.aggregateId(), e.payload()).join();
        e.markPublished(Instant.now());
    });
    // reservation, account도 동일 패턴
}
```

토픽명은 `TradingOutboxTopics`가 정한다 — 기본은 `<도메인prefix>.<eventType>`이지만, 이미 다른 서비스가 소비 중인 이벤트는 그 이름을 그대로 쓴다(`OrderFilled` → `orderFilled`, portfolio-service 계약).

### 코드 구현

```java
// OutboxWriter.java (실제 코드) — 트랜잭션 안에서 호출됨
public <REC> void record(OutboxOperations<REC> ops, String eventType, String aggregateId, Object payload) {
    Instant now = Instant.now();
    REC event = ops.newEvent(UUID.randomUUID(), eventType, aggregateId,
            objectMapper.writeValueAsString(payload), now);
    ops.save(event);
}
```

`OutboxOperations<REC>`라는 인터페이스로 추상화돼 있어서, `OutboxWriter`/`IdempotencyExecutor` 같은 공통 알고리즘 클래스는 **어떤 도메인의 리포지토리인지 몰라도** 동작한다 — 호출하는 쪽(각 도메인 서비스)이 자기 도메인의 구현체(`OrderOutboxOperations` 등)를 넘겨준다.

### 중복 발행 처리

Kafka 전송에 실패하면(또는 발행 후 컨슈머가 재시작하면) 같은 이벤트가 여러 번 전달될 수 있다(최소 1회 보장). 이를 수신하는 컨슈머는 상태 전이 자체가 멱등하도록 설계돼 있다 — 예를 들어 이미 CONVERTING이 아닌 예약에 `ReservationDue` 처리를 재시도하면 조용히 skip된다.

---

## 8. 데이터베이스 테이블 설명

> **갱신 사항**: 이전 초안은 계좌/주문 테이블만 있는 단일 스키마를 가정했지만, 실제로는 **3개 스키마**(`account`/`order_svc`/`reservation`)에 각각 도메인 테이블 + `outbox_events` + `idempotency_records`가 있다. 이번 갱신에서는 실제 Flyway 마이그레이션 파일을 직접 확인해 컬럼뿐 아니라 **CHECK 제약·UNIQUE 인덱스·FK**까지 정확히 반영했다 — 이전 버전에 있던 오탈자(주문 상태에 `REJECTED`가 있다고 적었던 부분, 실제로는 `FAILED`가 맞다)도 이번에 바로잡았다.

### `account.accounts` — 계좌

```sql
CREATE TABLE account.accounts (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / INACTIVE
  cash_krw    BIGINT NOT NULL DEFAULT 0,                -- 보유 현금(원 단위 정수)
  locked_krw  BIGINT NOT NULL DEFAULT 0,                -- 잠금 금액
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT uq_accounts_user_id UNIQUE (user_id),                                -- 1인당 계좌 1개
  CONSTRAINT chk_accounts_cash_non_negative CHECK (cash_krw >= 0),
  CONSTRAINT chk_accounts_locked_non_negative CHECK (locked_krw >= 0),
  CONSTRAINT chk_accounts_locked_not_exceed_cash CHECK (locked_krw <= cash_krw)   -- 잠금 금액이 현금을 못 넘음
);
-- 가용 금액 = cash_krw - locked_krw (DB 컬럼이 아니라 조회 시 계산)
```

**갱신 사항**: `status`는 원래 PostgreSQL `ENUM` 타입(`account.account_status`)으로 만들었다가, JPA `@Enumerated(EnumType.STRING)`과의 궁합을 맞추기 위해 나중에 `VARCHAR(20)`으로 바꾼 이력이 있다(마이그레이션 `V20260704`). `locked_krw <= cash_krw` CHECK 제약이 눈여겨볼 만한데, 애플리케이션 코드가 잔고 잠금 검증을 실수해도 DB가 마지막 방어선 역할을 한다.

금액을 `double`이 아닌 정수(`long`/`BIGINT`)로 저장하는 이유는 이전 초안과 동일 — 부동소수점 오차가 금융에서는 치명적이기 때문이다.

### `order_svc.orders` — 즉시 주문

```sql
CREATE TABLE order_svc.orders (
  id                  UUID PRIMARY KEY,
  user_id             UUID NOT NULL,
  account_id          UUID NOT NULL,          -- account 도메인 소유, 값만 복사 (FK 없음)
  symbol              VARCHAR(20) NOT NULL,   -- Market 도메인 소유, 값만 복사 (FK 없음)
  side                VARCHAR(10) NOT NULL,   -- BUY / SELL
  order_kind          VARCHAR(20) NOT NULL,   -- MARKET / LIMIT
  quantity            BIGINT NOT NULL,
  price_krw           BIGINT,                 -- LIMIT일 때만 값 존재
  reserved_amount_krw BIGINT NOT NULL DEFAULT 0,
  status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / FILLED / CANCELLED / FAILED
  parent_order_id     UUID,                   -- 정정 시 원 주문 참조 (self-FK)
  idempotency_key     TEXT NOT NULL,
  executed_at         TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),
  CONSTRAINT fk_orders_parent_order FOREIGN KEY (parent_order_id) REFERENCES order_svc.orders (id),
  CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
  CONSTRAINT chk_orders_price_krw_positive CHECK (price_krw IS NULL OR price_krw > 0),
  -- LIMIT 주문은 price_krw 필수, MARKET 주문은 price_krw가 없어야 함
  CONSTRAINT chk_orders_price_krw_by_kind CHECK (
      (order_kind = 'LIMIT' AND price_krw IS NOT NULL)
      OR (order_kind = 'MARKET' AND price_krw IS NULL)
  ),
  CONSTRAINT chk_orders_reserved_amount_non_negative CHECK (reserved_amount_krw >= 0)
);

-- 동일 계좌·동일 종목에 PENDING 주문 1건만 허용 (부분 유니크 인덱스)
CREATE UNIQUE INDEX uq_orders_account_symbol_pending
  ON order_svc.orders (account_id, symbol) WHERE status = 'PENDING';

-- 사용자별 주문 목록 조회(상태 필터 + 최신순 정렬) 가속
CREATE INDEX idx_orders_user_status_created_at
  ON order_svc.orders (user_id, status, created_at DESC, id DESC);
```

**갱신 사항 — proto와 실제 구현이 완전히 일치하진 않는다**: `trading_common.proto`의 `OrderStatus`에는 `ORDER_STATUS_REJECTED` 값이 정의돼 있지만, 실제 `order_svc.orders.status` 컬럼에는 그 값이 없다 — 주문 도메인이 실제로 쓰는 값은 `PENDING`/`FILLED`/`CANCELLED`/`FAILED` 4개뿐이다. 마찬가지로 `OrderKind`에는 `ORDER_KIND_AFTER_HOURS_CLOSE`가 있지만 이 값은 즉시 주문(order)이 아니라 **예약 주문(reservation) 쪽 `order_kind`가 재사용하는 값**이다 — `order_svc.orders`는 `MARKET`/`LIMIT` 두 개만 CHECK 제약으로 허용한다. proto가 두 도메인의 enum을 공유하다 보니 "정의는 돼 있지만 이 도메인에서는 안 쓰는 값"이 섞여 있는 셈이다.

`동일 계좌·동일 종목 PENDING 주문 1건만 허용`이라는 규칙(6절에서 다시 설명)은 애플리케이션 코드의 `existsBy...` 체크뿐 아니라 **부분 유니크 인덱스로 DB 레벨에서도 이중으로 강제**된다 — 동시 요청 경쟁 상황에서도 중복 PENDING 주문이 절대 생기지 않는다.

### `order_svc.executions` — 체결 (신규, 이전 초안엔 없던 테이블)

```sql
CREATE TABLE order_svc.executions (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id            UUID NOT NULL,
  executed_price_krw  BIGINT NOT NULL,
  executed_quantity   BIGINT NOT NULL,
  fee_krw             BIGINT NOT NULL,          -- 매수/매도 공통 0.015%
  tax_krw             BIGINT NOT NULL DEFAULT 0, -- 거래세, 매도만 0.18%
  net_amount_krw      BIGINT NOT NULL,
  executed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),  -- append-only: updated_at 없음

  CONSTRAINT uq_executions_order_id UNIQUE (order_id),   -- 체결 전엔 row 자체가 없음(1:1)
  CONSTRAINT fk_executions_order FOREIGN KEY (order_id) REFERENCES order_svc.orders (id),
  CONSTRAINT chk_executions_price_positive CHECK (executed_price_krw > 0),
  CONSTRAINT chk_executions_quantity_positive CHECK (executed_quantity > 0),
  CONSTRAINT chk_executions_fee_non_negative CHECK (fee_krw >= 0),
  CONSTRAINT chk_executions_tax_non_negative CHECK (tax_krw >= 0)
);

CREATE INDEX idx_executions_order_id ON order_svc.executions (order_id);
```

주문 1건당 체결 1건(전량 체결만 지원, 분할 체결 없음)이라 `order_id`에 UNIQUE + FK를 걸어 정합성을 강제한다.

### `reservation.reservations` — 예약 주문 (신규, 이전 초안엔 없던 테이블)

```sql
CREATE TABLE reservation.reservations (
  id                    UUID PRIMARY KEY,
  user_id               UUID NOT NULL,
  account_id            UUID NOT NULL,
  symbol                VARCHAR(20) NOT NULL,
  side                  VARCHAR(10) NOT NULL,   -- BUY / SELL
  timing                VARCHAR(20) NOT NULL,   -- OPEN / PREV_CLOSE / TODAY_CLOSE
  order_kind            VARCHAR(30) NOT NULL,   -- MARKET / LIMIT / AFTER_HOURS_CLOSE
  quantity              BIGINT NOT NULL,
  price_krw             BIGINT,                  -- LIMIT(시가+지정가)일 때만 값 존재
  scheduled_date        DATE NOT NULL,
  reserved_amount_krw   BIGINT NOT NULL DEFAULT 0,
  status                VARCHAR(20) NOT NULL DEFAULT 'RESERVED', -- RESERVED/CONVERTING/EXECUTED/CANCELLED/FAILED/EXPIRED
  converted_order_id    UUID,                    -- 시가+지정가 전환 완료 시에만 값 존재
  parent_reservation_id UUID REFERENCES reservation.reservations (id),  -- 정정 시 원 예약 참조(같은 스키마 내부 FK)
  idempotency_key       TEXT NOT NULL,
  expires_at            TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT uq_reservations_idempotency_key UNIQUE (idempotency_key),
  CONSTRAINT chk_reservations_side CHECK (side IN ('BUY', 'SELL')),
  -- timing×order_kind 조합은 정확히 4가지만 허용 (아래 표 참고)
  CONSTRAINT chk_reservations_timing_order_kind CHECK (
      (timing = 'OPEN' AND order_kind IN ('MARKET', 'LIMIT'))
      OR (timing IN ('TODAY_CLOSE', 'PREV_CLOSE') AND order_kind = 'AFTER_HOURS_CLOSE')
  ),
  CONSTRAINT chk_reservations_price_krw CHECK (
      (order_kind = 'LIMIT' AND price_krw IS NOT NULL)
      OR (order_kind != 'LIMIT' AND price_krw IS NULL)
  )
);

-- 동일 계좌·동일 종목에 RESERVED 예약 1건만 허용(주문의 PENDING 유니크 인덱스와 같은 패턴)
CREATE UNIQUE INDEX uq_reservations_account_symbol_reserved
  ON reservation.reservations (account_id, symbol) WHERE status = 'RESERVED';

-- 배치가 "오늘 처리할 RESERVED 건"을 조회할 때 타는 인덱스
CREATE INDEX idx_reservations_batch_lookup
  ON reservation.reservations (scheduled_date, status, timing) WHERE status = 'RESERVED';

-- 사용자별 예약 목록 페이징 조회 가속
CREATE INDEX idx_reservations_user_status_created_id
  ON reservation.reservations (user_id, status, created_at DESC, id DESC);
```

`timing × order_kind` 조합은 코드(4절)에서 본 것처럼 딱 4개만 허용된다 — CHECK 제약이 애플리케이션 검증과 동일한 규칙을 DB에도 걸어둔 것이다.

| timing | order_kind | 의미 |
|---|---|---|
| OPEN | MARKET | 시가 + 시장가 |
| OPEN | LIMIT | 시가 + 지정가 (유일하게 order_svc로 전환되는 케이스) |
| PREV_CLOSE | AFTER_HOURS_CLOSE | 전일종가 |
| TODAY_CLOSE | AFTER_HOURS_CLOSE | 당일종가 |

**갱신 사항**: `parent_reservation_id` 컬럼은 정정(AmendReservation) 시 원 예약을 추적하기 위한 것으로, `reservation.reservations` 테이블 내부를 가리키는 self-FK다. order_svc의 `parent_order_id`와 완전히 같은 패턴이다.

### `account.consumed_events` — Kafka 컨슈머 멱등성 (신규)

```sql
CREATE TABLE account.consumed_events (
  event_id     UUID PRIMARY KEY,   -- 같은 이벤트 재수신 시 존재 여부만 확인
  event_type   VARCHAR(120) NOT NULL,
  consumed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### `<schema>.outbox_events` — 발행 대기 이벤트 (3개 스키마 각각 존재)

```sql
CREATE TABLE order_svc.outbox_events (
  id           UUID PRIMARY KEY,
  event_type   VARCHAR(120) NOT NULL,   -- "OrderPlaced", "OrderFilled" 등
  aggregate_id VARCHAR(120) NOT NULL,
  payload      TEXT NOT NULL,           -- JSON 형식의 이벤트 내용
  occurred_at  TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ              -- NULL이면 미발행
);

-- 미발행 이벤트만 빠르게 조회하기 위한 인덱스(3개 스키마 각각 존재)
CREATE INDEX idx_order_svc_outbox_events_pending
  ON order_svc.outbox_events (occurred_at) WHERE published_at IS NULL;
```
account/reservation 스키마에도 동일 구조로 각각 존재한다(엔티티명은 `AccountOutboxEvent`/`OrderOutboxEvent`/`ReservationOutboxEvent`로 클래스명 충돌을 회피).

### `<schema>.idempotency_records` — 멱등성 기록 (3개 스키마 각각 존재)

```sql
CREATE TABLE order_svc.idempotency_records (
  actor_id         VARCHAR(120) NOT NULL,
  operation        VARCHAR(200) NOT NULL,  -- gRPC full method name
  idempotency_key  VARCHAR(64) NOT NULL,
  request_hash     VARCHAR(64) NOT NULL,
  response_payload BYTEA NOT NULL,         -- AES-256-GCM 암호화된 값 (평문 아님!)
  response_type    VARCHAR(200) NOT NULL,
  grpc_code        VARCHAR(40) NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at       TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (actor_id, operation, idempotency_key)
);
```
account/reservation 스키마에도 동일 구조로 각각 존재한다(`AccountIdempotencyRecord`/`OrderIdempotencyRecord`/`ReservationIdempotencyRecord`).

복합 기본키 설명은 이전 초안과 동일 — 같은 사용자가 다른 작업에, 또는 다른 사용자가 같은 UUID를 써도 충돌하지 않는다.
---

## 9. API 계약 (Proto)

> **갱신 사항**: 이전 초안은 `trading.proto` 하나를 가정했지만, 실제로는 4개 파일로 나뉘어 있다(2절 참고). 예약 주문 RPC도 "미구현 예정"이 아니라 전부 구현되어 있다.

### 열거형 값 (`trading_common.proto`)

| 열거형 | 값 |
|--------|-----|
| `OrderSide` | `BUY`, `SELL` |
| `OrderKind` | `MARKET`, `LIMIT`, `AFTER_HOURS_CLOSE` |
| `OrderStatus` | proto 정의: `PENDING`, `FILLED`, `CANCELLED`, `REJECTED`. **단, order_svc DB/엔티티가 실제로 쓰는 값은 `PENDING`/`FILLED`/`CANCELLED`/`FAILED`이며 `REJECTED`는 쓰이지 않는다**(8절 갱신 사항 참고) |
| `ReservationTiming` | `OPEN`(시가), `PREV_CLOSE`(전일종가), `TODAY_CLOSE`(당일종가) |
| `ReservationStatus` | `RESERVED`, `EXECUTED`, `CANCELLED`, `CONVERTING`, `FAILED`, `EXPIRED` |

### RPC 목록 — Account (`account.proto`)

| RPC | 요청 | 응답 | 멱등성 키 필요 |
|-----|------|------|--------------|
| `GetBalance` | `user_id` | 잔고 정보 | 불필요 (읽기) |

### RPC 목록 — Order (`order.proto`)

| RPC | 요청 | 응답 | 멱등성 키 필요 |
|-----|------|------|--------------|
| `ListOrders` | `user_id`, `status`(선택), `page` | 주문 목록(체결 정보 포함) | 불필요 (읽기) |
| `PlaceOrder` | `user_id`, `symbol`, `side`, `kind`, `quantity`, `price`, `command_metadata` | 생성/체결된 주문 | **필요** |
| `CancelOrder` | `user_id`, `order_id`, `command_metadata` | 취소된 주문, 반환 금액 | **필요** |
| `AmendOrder` | `user_id`, `order_id`, `quantity`, `price`, `command_metadata` | 새로 생성된 정정 주문 | **필요** |
| `ExpirePendingOrders` | (없음) | 취소된 건수 | 불필요 (배치 전용, 시스템 호출) |

### RPC 목록 — Reservation (`reservation.proto`, 전부 신규)

| RPC | 요청 | 응답 | 비고 |
|-----|------|------|------|
| `ListReservations` | `user_id`, `status`(선택), `page` | 예약 목록 | 조회 |
| `PlaceReservation` | `symbol`, `side`, `timing`, `kind`, `quantity`, `price`(선택), `scheduled_date`(선택), `command_metadata` | 생성된 예약 | 멱등 |
| `CancelReservation` | `user_id`, `reservation_id`, `command_metadata` | 취소된 예약, 반환 금액 | 멱등 |
| `AmendReservation` | `user_id`, `reservation_id`, 변경할 필드들, `command_metadata` | 새로 생성된 정정 예약 | 멱등 |
| `ProcessOpenLimitReservations` / `ProcessPrevCloseReservations` / `ProcessTodayCloseReservations` | `scheduled_date` | 처리 건수 | 배치 전용(일별) |
| `ListOpenLimitReservations` → `ProcessSingleOpenLimitReservation` | `scheduled_date` / `reservation_id` | id 목록 / 처리 여부 | 배치 전용(건별) |
| `ListStaleConvertingReservations` → `FailStaleConvertingReservation` | 〃 | 〃 | CONVERTING 타임아웃 정리 |
| `ListExpirableReservations` → `ExpireReservation` | 〃 | 〃 | 만료 처리 |
| `MarkReservationConverted` | `reservation_id`, `converted_order_id` | 갱신된 예약 | 내부/배치 전용(현재는 Kafka `ReservationConverted` 이벤트 경로로 대체 사용 중) |

> `command_metadata.idempotency_key`는 클라이언트가 생성한 UUID다. 쓰기 RPC에는 반드시 포함해야 하며, 같은 UUID를 `x-idempotency-key` gRPC 헤더에도 함께 보내야 한다. 배치 전용 RPC는 시스템 호출이라 이 규칙에서 예외다(내부망으로만 보호).

---

## 10. 핵심 설계 결정 요약

| 결정 | 이유 |
|------|------|
| REST 대신 gRPC | 서비스 간 통신에 적합. 계약이 명확하고, 이진 형식으로 빠르다 |
| 금액을 `long`(정수, `_krw` 접미사)으로 저장 | `double`/`float`의 부동소수점 오차가 금융에서 치명적이다 |
| 멱등성 키 강제 | 네트워크 오류로 인한 중복 주문/예약을 방지한다 |
| 응답 저장값 암호화(AES-256-GCM) | 멱등성 레코드에 담긴 과거 응답 내용을 DB 레벨에서 평문 노출하지 않는다 *(신규)* |
| Outbox 패턴 | DB 저장과 Kafka 발행의 원자성을 보장한다. 중간에 서버가 죽어도 데이터가 유실되지 않는다 |
| 도메인별 스키마 + 도메인별 outbox/idempotency 테이블 | account/order_svc/reservation을 하나의 서비스로 배포하되 데이터는 완전히 분리해, 도메인 간 결합을 낮춘다 *(신규)* |
| 예약↔주문 간 전 구간 Kafka 비동기(동기 호출 없음) | reservation이 order를 동기 호출하면 두 도메인이 강결합된다 — 완전 비동기로 결합도를 낮춘다 *(신규)* |
| `public setter` 금지 | 상태 변경을 행위 메서드로 제한해 잘못된 상태 전이를 방지한다 |
| 도메인 서비스 분리 | gRPC 계층(요청/응답 형식)과 비즈니스 로직을 분리해 각각 독립적으로 테스트할 수 있다 |
| self-invocation 회피용 Executor 분리 | Spring `@Transactional` 프록시가 우회되는 것을 막기 위해 배치/체결의 건별 트랜잭션을 별도 `@Service`로 뺐다 *(신규)* |
| 정규장 시간/거래일 판정을 market-service에 위임 | trading-service가 자체적으로 시간을 계산하면 서비스마다 판단이 어긋날 수 있다 *(신규)* |
| 트랜잭션 범위 | 도메인 변경 + outbox + 멱등성 레코드가 하나의 트랜잭션으로 묶여 일부만 성공하는 상황이 없다 |
| 초기 잔고 자동 지급(1억원) | 모의투자 목적으로 신규 계좌에 시드머니를 즉시 지급한다. 지급 근거(입금 이력)를 남기는 절차는 아직 없다 |

---

## 11. 테스트는 어떻게 검증하나

이전 초안에는 테스트 얘기가 아예 없었다. 이번에 실제 테스트 코드(26개 파일)를 열어봤더니, "이 코드가 진짜 의도대로 동작하는지"를 계층마다 다른 방식으로 확인하고 있었다 — 그 방식들을 소개한다.

### 왜 계층마다 검증 방식이 다른가

**비유**: 요리를 검증하는 방법은 재료에 따라 다르다. 채소는 손으로 만져보면 되지만(순수 단위 테스트), 고기는 온도계로 찔러봐야 하고(Mock으로 외부 의존성 대체), 완성된 코스 요리는 실제로 손님상에 나가는 절차 그대로 재현해봐야 한다(in-process 서버). Trading Service 테스트도 계층별로 딱 맞는 방식을 골라 쓰고 있다.

| 계층 | 검증 방식 | 왜 이렇게 하는가 |
|---|---|---|
| Entity (`OrderEntity`, `ReservationEntity` 등) | Mock 없이 순수 단위 테스트 | 상태 전이 로직 자체는 외부 의존성이 없으므로 그냥 호출해서 결과만 확인하면 된다 |
| 도메인 서비스 (`DefaultOrderService` 등) | Mockito로 리포지토리/외부 클라이언트를 Mock 처리 | 실제 DB 없이도 "이 조건일 때 이 메서드를 호출하는가"를 검증할 수 있다 |
| gRPC 서비스 (`OrderGrpcService` 등) | Mockito + `@ParameterizedTest`/`@EnumSource` | 에러코드 → gRPC Status 매핑처럼 **경우의 수가 많은 로직**은 하나하나 손으로 테스트를 안 쓰고, enum 값 전체를 자동으로 순회하며 빠짐없이 검증한다 |
| Kafka 컨슈머 (`ReservationDueConsumer` 등) | Mockito + **진짜 `ObjectMapper`** | 여기서 진짜로 확인하고 싶은 건 "역직렬화가 실제로 되는가"이기 때문에, 이 부분만큼은 Mock으로 대체하면 의미가 없다 |
| 외부 gRPC 클라이언트 (`DefaultMarketSessionClient` 등) | **in-process gRPC 서버**를 직접 띄움 | Mockito로 스텁을 만들면 "요청을 특정 문자열로 만들어 보냈는가"까지는 확인 못 한다. 진짜 gRPC 프로토콜로 왕복시켜야 요청/응답 매핑이 실제로 맞는지 확인된다 |
| 멱등성 처리(`IdempotencyExecutor`) | Mockito, 4단계 분기(해시 계산→조회→트랜잭션 실행→동시 경합 시 재조회) 전부 검증 | 6절에서 설명한 알고리즘이 정확히 코드에 반영됐는지 단계별로 확인한다 |

### 예시 — gRPC 에러코드 매핑을 어떻게 "빠짐없이" 테스트하는가

`OrderErrorCode`에 정의된 에러 코드가 10개가 넘는데, 이걸 gRPC `Status`로 바꾸는 로직(`toGrpcException`)이 하나라도 빠지면 클라이언트가 알 수 없는 오류를 받게 된다. 그래서 손으로 10개 테스트 메서드를 쓰는 대신, JUnit의 `@EnumSource`로 **enum 값 전체를 자동으로 순회**한다.

```java
@ParameterizedTest(name = "{0}")
@EnumSource(OrderErrorCode.class)
@DisplayName("OrderErrorCode 전체 값이 예외 없이 gRPC Status로 매핑된다")
void shouldMapEveryOrderErrorCodeToSomeGrpcStatus(OrderErrorCode errorCode) {
    when(orderService.placeOrder(eq(userId), any())).thenThrow(new OrderException(errorCode));
    // ... PlaceOrder 호출 ...
    // 어떤 에러코드가 새로 추가되더라도, 매핑이 누락되면 이 테스트가 바로 실패한다
}
```

새로운 `OrderErrorCode` 값이 추가됐는데 `toGrpcException`에 매핑을 깜빡 잊으면, 이 테스트가 자동으로 잡아준다 — enum과 매핑 로직이 항상 같은 개수를 유지하도록 강제하는 셈이다.


### 로컬에서 테스트 실행하기

```bash
./gradlew :trading-service:test
```

DB/Kafka/외부 서비스가 필요한 테스트는 거의 없다 — 대부분 Mockito Mock이나 in-process gRPC 서버로 대체되어 있어서, 별도 인프라 없이도 `./gradlew test` 한 줄로 대부분 검증된다. 실제 인프라가 필요한 건 서비스를 띄워서 손으로 확인할 때(8절에서 다룬 로컬 실행/grpcurl 시나리오)뿐이다.

---

*이 문서는 이전 온보딩 초안의 설명 방식(비유, ASCII 다이어그램, 코드 스니펫)을 유지하면서, 내용은 dev 브랜치의 실제 코드·Flyway 마이그레이션·테스트 코드를 기준으로 갱신했다. "갱신 사항"/"신규"로 표시된 부분이 이전 초안과 달라졌거나 새로 추가된 지점이다.*