# Trading Service 구조 설명

이 문서는 `trading-service`의 구조와 동작 방식을 설명한다.  
개발 경험이나 gRPC 지식 없이도 읽을 수 있도록 작성했다.

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

---

## 1. Trading Service가 하는 일

Trading Service는 사용자의 **주식 매매 주문**을 처리하는 서비스다.  
구체적으로 다음 기능을 담당한다.

| 기능 | 설명 |
|------|------|
| 잔고 조회 | 사용자의 보유 현금, 예약 금액, 가용 금액을 반환한다 |
| 주문 생성 | 매수/매도 주문을 생성하고, 매수 시 필요한 금액을 예약한다 |
| 주문 취소 | 대기 중인 주문을 취소하고, 예약된 금액을 돌려준다 |
| 주문 목록 조회 | 사용자의 주문 내역을 조회한다 |

---

## 2. gRPC란 무엇인가

### REST API와의 비교

일반적인 웹 서비스는 **REST API**를 사용한다. 예를 들어 주문을 생성할 때 HTTP로 JSON을 주고받는다.

```
POST /orders
{ "symbol": "AAPL", "side": "BUY", "quantity": 10, "price": 150 }
```

**gRPC**는 서비스 간 통신을 위해 설계된 다른 방식이다. JSON 대신 **Protocol Buffers(proto)** 라는 이진 형식을 사용한다.

### gRPC를 쓰는 이유

- **성능**: JSON보다 데이터 크기가 작고 직렬화/역직렬화가 빠르다
- **계약이 명확하다**: `.proto` 파일 하나가 API의 유일한 정의다. 스키마가 어긋나면 컴파일 단계에서 오류가 난다
- **마이크로서비스에 적합하다**: 서비스 간 통신에 특화되어 있으며, 서버와 클라이언트 코드를 자동으로 생성한다

### proto 파일이란

`.proto` 파일은 API의 형태를 정의하는 설계도다. 이 파일 하나로 Java 서버 코드와 클라이언트 코드가 자동 생성된다.

```protobuf
// proto/candle/trading/v1/trading.proto
service TradingService {
  rpc GetBalance(GetBalanceRequest) returns (GetBalanceResponse);
  rpc PlaceOrder(PlaceOrderRequest) returns (PlaceOrderResponse);
  rpc CancelOrder(CancelOrderRequest) returns (CancelOrderResponse);
}
```

위 정의는 "TradingService라는 gRPC 서비스가 있고, 잔고 조회 / 주문 생성 / 주문 취소 기능을 제공한다"는 계약이다.

### gRPC 요청 흐름

```
클라이언트 앱
    │
    │  gRPC 호출 (이진 데이터)
    ▼
[인터셉터] ← 모든 요청이 먼저 거치는 관문
    │          (인증 정보 추출, 멱등성 키 검증)
    ▼
[gRPC 엔드포인트] ← 요청 파싱, 응답 변환
    │
    ▼
[도메인 서비스] ← 실제 비즈니스 로직
    │
    ▼
[데이터베이스]
```

---

## 3. 서비스 구조 한눈에 보기

```
services/trading-service/
└── src/main/java/org/profit/candle/trading/
    │
    ├── grpc/
    │   └── TradingGrpcService.java       ← gRPC 진입점. 요청 받고 응답 반환
    │
    ├── domain/
    │   ├── TradingDomainService.java     ← 핵심 비즈니스 로직 (잔고 차감, 주문 생성 등)
    │   ├── entity/
    │   │   ├── OrderEntity.java          ← 주문 데이터 (DB 테이블 매핑)
    │   │   └── AccountBalanceEntity.java ← 잔고 데이터 (DB 테이블 매핑)
    │   ├── repository/
    │   │   ├── OrderRepository.java      ← 주문 데이터 읽기/쓰기
    │   │   └── AccountBalanceRepository.java
    │   └── (OrderSideValue, OrderStatusValue, OrderKindValue) ← 열거형 값 정의
    │
    ├── idempotency/                      ← 중복 요청 방지 시스템
    │   ├── IdempotencyServerInterceptor.java  ← 모든 요청에서 멱등성 키 추출
    │   ├── IdempotencyExecutor.java           ← 중복 요청 감지 및 재생
    │   ├── IdempotencyContext.java            ← 요청별 컨텍스트 정보 보관
    │   ├── RequestHasher.java                 ← 요청 내용의 지문(해시) 계산
    │   └── entity/IdempotencyRecord.java      ← 처리 결과 저장 (DB)
    │
    └── event/
        ├── OutboxWriter.java             ← 이벤트를 DB에 기록
        └── entity/OutboxEvent.java       ← 발행 대기 이벤트 (DB 테이블 매핑)
```

---

## 4. 계층별 역할 설명

### 4-1. gRPC 엔드포인트 (`grpc/TradingGrpcService.java`)

**비유:** 식당의 웨이터. 손님(클라이언트)의 주문을 받아 주방(도메인 서비스)에 전달하고, 완성된 음식(응답)을 손님에게 내준다.

하는 일:
- 들어온 gRPC 메시지를 Java 객체로 변환한다
- 인증된 사용자 ID를 검증한다
- 도메인 서비스를 호출한다
- 결과를 gRPC 메시지 형태로 변환해 반환한다

하지 않는 일:
- 비즈니스 규칙을 직접 판단하지 않는다 (잔고 충분한지 등)
- 데이터베이스에 직접 접근하지 않는다 (주문 목록 조회는 예외: 단순 조회라 직접 접근)

```java
// TradingGrpcService.java 중 주문 생성 부분
@Override
public void placeOrder(PlaceOrderRequest request, StreamObserver<PlaceOrderResponse> observer) {
    String actor = requireActor(request.getUserId());       // 1. 인증 검증
    var command = new TradingDomainService.PlaceOrderCommand(
            request.getSymbol(), toSide(request.getSide()),  // 2. proto → Java 변환
            toKind(request.getKind()), request.getQuantity(), request.getPrice());

    PlaceOrderResponse response = idempotencyExecutor.execute(  // 3. 멱등성 처리 후
            request, PlaceOrderResponse.parser(),
            () -> PlaceOrderResponse.newBuilder()
                    .setOrder(toProto(domain.placeOrder(actor, command)))  // 도메인 호출
                    .build());

    observer.onNext(response);    // 4. 응답 반환
    observer.onCompleted();
}
```

### 4-2. 도메인 서비스 (`domain/TradingDomainService.java`)

**비유:** 식당의 주방장. 실제로 요리를 한다. 재료(데이터)를 확인하고, 레시피(비즈니스 규칙)에 따라 처리한다.

하는 일:
- 주문 전 잔고가 충분한지 확인한다
- 동일 종목에 이미 대기 중인 주문이 있으면 거부한다
- 매수 주문 시 필요 금액(주문금액 + 수수료)을 예약한다
- 주문 취소 시 예약된 금액을 돌려준다
- 이벤트를 Outbox에 기록한다

```java
// TradingDomainService.java 중 주문 생성 핵심 로직
public OrderEntity placeOrder(String actorId, PlaceOrderCommand command) {
    // 1. 입력값 검증
    if (command.quantity() <= 0 || command.price() <= 0) {
        throw Status.INVALID_ARGUMENT.withDescription("quantity와 price는 양수여야 합니다").asRuntimeException();
    }
    // 2. 중복 주문 방지 (같은 종목에 이미 대기 중인 주문이 있으면 거부)
    if (orderRepository.existsByUserIdAndSymbolAndStatus(actorId, command.symbol(), OrderStatusValue.PENDING)) {
        throw Status.FAILED_PRECONDITION.withDescription("해당 종목에 이미 대기 중인 주문이 있습니다").asRuntimeException();
    }

    // 3. 매수 주문이면 잔고 예약
    long amount = command.price() * command.quantity();
    long fee = Math.round(amount * FEE_RATE);  // 수수료 0.015%
    long reserved = 0;
    if (command.side() == OrderSideValue.BUY) {
        AccountBalanceEntity balance = loadOrCreateBalance(actorId);
        reserved = amount + fee;
        if (balance.availableCash() < reserved) {
            throw Status.FAILED_PRECONDITION.withDescription("가용 금액이 부족합니다").asRuntimeException();
        }
        balance.reserve(reserved);  // 예약 처리
        balanceRepository.save(balance);
    }

    // 4. 주문 저장
    OrderEntity order = new OrderEntity(...);
    orderRepository.save(order);

    // 5. 이벤트 기록 (나중에 Kafka로 발행될 예정)
    outboxWriter.record("OrderPlaced", order.id(), new OrderPlacedPayload(...));
    return order;
}
```

### 4-3. Entity (`domain/entity/`)

**비유:** 데이터베이스 테이블의 Java 표현. 하나의 Entity 객체가 테이블의 한 행(row)에 대응한다.

**중요 설계 원칙:**
- `public setter`(외부에서 직접 필드를 바꾸는 메서드)를 만들지 않는다
- 상태 변경은 의미 있는 메서드로만 한다

```java
// OrderEntity.java
public class OrderEntity {
    private OrderStatusValue status;
    // ...

    // ❌ 이렇게 하지 않는다
    // public void setStatus(OrderStatusValue status) { this.status = status; }

    // ✅ 이렇게 한다 — "취소된다"는 행위로 표현
    public void markCancelled() { this.status = OrderStatusValue.CANCELLED; }
}
```

```java
// AccountBalanceEntity.java
public class AccountBalanceEntity {
    private long cash;
    private long reservedBalance;

    public long availableCash() { return cash - reservedBalance; }  // 가용 금액 = 보유 현금 - 예약 금액

    public void reserve(long amount) { this.reservedBalance += amount; }         // 예약
    public void releaseReservation(long amount) { this.reservedBalance -= amount; } // 예약 해제
    public void debit(long amount) { this.cash -= amount; }   // 출금 (체결 시 사용)
    public void credit(long amount) { this.cash += amount; }  // 입금
}
```

---

## 5. 주요 기능 흐름

### 5-1. 주문 생성 흐름 (PlaceOrder)

```
클라이언트
    │
    │  PlaceOrderRequest {
    │    user_id: "user-123",
    │    symbol: "AAPL",
    │    side: BUY,
    │    kind: LIMIT,
    │    quantity: 10,
    │    price: 150_00 (원 단위 정수, 15,000원),
    │    command_metadata: { idempotency_key: "uuid-xxxx" }
    │  }
    │
    ▼
[IdempotencyServerInterceptor]
    │  x-idempotency-key, x-user-id 헤더 추출 → IdempotencyContext 생성
    │
    ▼
[TradingGrpcService.placeOrder]
    │  actor 검증, proto → PlaceOrderCommand 변환
    │
    ▼
[IdempotencyExecutor.execute]
    │  이 idempotency_key로 처리한 기록이 있는가?
    │  ├─ 있음 (같은 내용) → 저장된 응답을 그대로 반환 (실제 처리 안 함)
    │  ├─ 있음 (다른 내용) → ALREADY_EXISTS 오류 반환
    │  └─ 없음 → 아래 트랜잭션 실행
    │
    ▼
[트랜잭션 시작] ─────────────────────────────────────────
    │
    ├─ TradingDomainService.placeOrder
    │   ├─ 입력값 검증 (수량·가격 > 0)
    │   ├─ 동일 종목 PENDING 주문 중복 여부 확인
    │   ├─ 잔고 조회 (없으면 초기 잔고 1,000만원으로 생성)
    │   ├─ 가용 잔고 >= 주문금액+수수료 확인
    │   ├─ 잔고 예약 (reservedBalance += 주문금액+수수료)
    │   ├─ 주문 저장 (status=PENDING)
    │   └─ OutboxWriter.record("OrderPlaced", ...)
    │
    └─ IdempotencyRecord 저장 (응답 내용 포함)
[트랜잭션 종료] ─────────────────────────────────────────
    │
    ▼
클라이언트에 PlaceOrderResponse 반환
```

### 5-2. 주문 취소 흐름 (CancelOrder)

```
클라이언트
    │  CancelOrderRequest { user_id, order_id, command_metadata }
    ▼
[멱등성 처리 → 트랜잭션]
    │
    ├─ 주문 조회 (없거나 다른 사용자 소유면 NOT_FOUND)
    ├─ 주문 상태 확인 (PENDING이 아니면 FAILED_PRECONDITION)
    ├─ 예약 금액 해제 (reservedBalance -= reserved_amount)
    ├─ 주문 상태 변경 (status=CANCELLED)
    └─ OutboxWriter.record("OrderCancelled", ...)
    │
    ▼
CancelOrderResponse { order, released_amount } 반환
```

### 5-3. 잔고 조회 흐름 (GetBalance)

```
클라이언트
    │  GetBalanceRequest { user_id }
    ▼
[TradingGrpcService.getBalance]
    │  actor 검증
    ▼
[TradingDomainService.getBalance]
    │  잔고 조회 (없으면 초기 잔고 1,000만원으로 자동 생성)
    ▼
GetBalanceResponse {
  cash: 10_000_000,          // 보유 현금
  reserved_balance: 150_000, // 예약된 금액
  available_cash: 9_850_000  // 실제 사용 가능한 금액
} 반환
```

---

## 6. 멱등성이란 무엇이고 왜 필요한가

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

**1. `IdempotencyServerInterceptor`** — 모든 gRPC 요청의 관문

```
gRPC 요청 도착
    │
    ▼
x-idempotency-key 헤더 추출
    ├─ 형식 검증 (UUID 형식인지, 64자 이하인지)
    └─ IdempotencyContext 생성 (actor_id + operation + idempotency_key)
    │
    ▼
컨텍스트를 gRPC 스레드에 전달 (다음 처리 단계에서 꺼내 씀)
```

**2. `RequestHasher`** — 요청 내용의 지문 계산

같은 키를 재사용하면서 다른 내용을 보내는 악의적 요청을 감지한다.  
요청 내용(operation + actor_id + 요청 메시지)을 SHA-256으로 해시해 저장한다.

**3. `IdempotencyExecutor`** — 핵심 처리 알고리즘

```
멱등성 키로 기존 레코드 조회
    │
    ├─ 없음 → 새로 처리 (도메인 로직 실행 + 결과 저장)
    │
    ├─ 있음 + 같은 내용 → 저장된 응답 그대로 반환 (재처리 없음)
    │
    └─ 있음 + 다른 내용 → ALREADY_EXISTS 오류 (키 재사용 거부)
```

**동시 요청 처리:**  
두 요청이 동시에 도착해 둘 다 "기록 없음"으로 판단하면 DB의 unique key 제약이 충돌을 감지한다.  
나중에 도착한 요청은 먼저 처리된 결과를 재조회해 반환한다.

### 처리 보장 범위

도메인 변경(잔고 예약, 주문 저장) + Outbox 이벤트 기록 + 멱등성 레코드 저장이 **하나의 트랜잭션**으로 묶인다.  
세 가지 중 하나라도 실패하면 모두 롤백된다.

```java
// IdempotencyExecutor.java
transactionTemplate.execute(status -> {
    R response = command.get();          // 도메인 변경 + outbox 기록
    repository.saveAndFlush(new IdempotencyRecord(...)); // 멱등성 기록
    return response;
});
```

---

## 7. Outbox 패턴이란 무엇이고 왜 필요한가

### 문제 상황

주문이 생성됐을 때 다른 서비스(포트폴리오, 알림 등)에도 "주문이 생성됐다"는 사실을 알려야 한다.  
가장 단순한 방법은 DB 저장 후 Kafka에 메시지를 보내는 것이지만, 이 방식은 위험하다.

```
❌ 잘못된 방식
    ├─ DB에 주문 저장 ← 성공
    └─ Kafka에 메시지 전송 ← 실패!
         → DB에는 주문이 있지만 다른 서비스는 모른다 (데이터 불일치)

❌ 순서를 바꿔도 같은 문제
    ├─ Kafka에 메시지 전송 ← 성공
    └─ DB에 주문 저장 ← 실패!
         → 다른 서비스는 주문이 있다고 알지만 실제로는 없다
```

### Outbox 패턴의 해결책

DB에 이벤트를 직접 기록하고, **별도의 발행자**가 나중에 Kafka로 전송한다.

```
✅ Outbox 패턴

[하나의 트랜잭션]
    ├─ DB에 주문 저장
    └─ DB의 outbox_events 테이블에 이벤트 기록
    → 둘 다 성공하거나 둘 다 실패 (트랜잭션으로 보장)

[별도 발행자 — 나중에 실행]
    ├─ outbox_events에서 미발행 이벤트 조회
    ├─ Kafka로 전송
    └─ 발행 완료 처리 (published_at 기록)
```

### 코드 구현

```java
// OutboxWriter.java — 트랜잭션 안에서 호출됨
public void record(String eventType, String aggregateId, Object payload) {
    outboxEventRepository.save(new OutboxEvent(
            UUID.randomUUID(),    // 이벤트 고유 ID (중복 처리에 활용)
            eventType,            // "OrderPlaced", "OrderCancelled" 등
            aggregateId,          // 주문 ID
            objectMapper.writeValueAsString(payload),  // JSON 직렬화
            Instant.now()));
}
```

```java
// TradingDomainService.java — 주문 저장 직후 호출
orderRepository.save(order);
outboxWriter.record("OrderPlaced", order.id(), new OrderPlacedPayload(
        order.id(), actorId, order.symbol(), order.side().name(),
        order.quantity(), order.price(), reserved));
```

### 중복 발행 처리

Kafka 전송에 실패하면 재시도한다. 재시도 과정에서 같은 이벤트가 여러 번 전송될 수 있다(최소 1회 보장).  
이를 수신하는 서비스는 `event ID`를 기준으로 이미 처리한 이벤트인지 확인해야 한다.

---

## 8. 데이터베이스 테이블 설명

### `account_balances` — 사용자 잔고

```sql
CREATE TABLE account_balances (
  user_id          VARCHAR(120) PRIMARY KEY,  -- 사용자 ID
  cash             BIGINT NOT NULL DEFAULT 0, -- 보유 현금 (원 단위 정수)
  reserved_balance BIGINT NOT NULL DEFAULT 0  -- 주문으로 예약된 금액
);
-- 가용 금액 = cash - reserved_balance (DB 컬럼이 아니라 계산값)
```

금액을 `double`이 아닌 `long`(정수)으로 저장하는 이유: 부동소수점 오차가 금융에서는 치명적이기 때문이다.  
예: `10_000_000` = 1,000만 원.

### `orders` — 주문

```sql
CREATE TABLE orders (
  id              VARCHAR(40) PRIMARY KEY,  -- UUID
  user_id         VARCHAR(120) NOT NULL,    -- 주문한 사용자
  symbol          VARCHAR(40) NOT NULL,     -- 종목 코드 (예: "AAPL")
  side            VARCHAR(16) NOT NULL,     -- "BUY" 또는 "SELL"
  kind            VARCHAR(24) NOT NULL,     -- "MARKET", "LIMIT", "AFTER_HOURS_CLOSE"
  quantity        BIGINT NOT NULL,          -- 수량
  price           BIGINT NOT NULL,          -- 주문 가격
  status          VARCHAR(16) NOT NULL,     -- "PENDING", "FILLED", "CANCELLED", "REJECTED"
  parent_order_id VARCHAR(40),              -- 정정 주문의 경우 원 주문 ID
  reserved_amount BIGINT NOT NULL DEFAULT 0, -- 예약된 금액 (취소 시 반환에 사용)
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### `outbox_events` — 발행 대기 이벤트

```sql
CREATE TABLE outbox_events (
  id           UUID PRIMARY KEY,        -- 이벤트 고유 ID
  event_type   VARCHAR(120) NOT NULL,   -- "OrderPlaced", "OrderCancelled"
  aggregate_id VARCHAR(120) NOT NULL,   -- 관련 주문 ID
  payload      TEXT NOT NULL,           -- JSON 형식의 이벤트 내용
  occurred_at  TIMESTAMPTZ NOT NULL,    -- 이벤트 발생 시각
  published_at TIMESTAMPTZ              -- Kafka 발행 시각 (NULL이면 미발행)
);

-- 미발행 이벤트만 빠르게 조회하기 위한 인덱스
CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at)
  WHERE published_at IS NULL;
```

### `idempotency_records` — 멱등성 기록

```sql
CREATE TABLE idempotency_records (
  actor_id         VARCHAR(120) NOT NULL,  -- 요청한 사용자 ID
  operation        VARCHAR(200) NOT NULL,  -- gRPC 메서드 이름 (예: "candle.trading.v1.TradingService/PlaceOrder")
  idempotency_key  VARCHAR(64) NOT NULL,   -- 클라이언트가 보낸 UUID
  request_hash     CHAR(64) NOT NULL,      -- 요청 내용의 SHA-256 해시 (내용 변조 감지)
  response_payload BYTEA NOT NULL,         -- 성공 응답을 이진 직렬화한 값 (재생용)
  response_type    VARCHAR(200) NOT NULL,  -- 응답 타입 이름
  grpc_code        VARCHAR(40) NOT NULL DEFAULT 'OK',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at       TIMESTAMPTZ NOT NULL,   -- 기본 24시간 후 만료
  PRIMARY KEY (actor_id, operation, idempotency_key)
);
```

복합 기본키 `(actor_id, operation, idempotency_key)` 설명:
- 같은 사용자가 다른 작업(PlaceOrder vs CancelOrder)에 같은 UUID를 써도 충돌하지 않는다
- 다른 사용자가 같은 UUID를 써도 충돌하지 않는다

---

## 9. API 계약 (Proto)

`proto/candle/trading/v1/trading.proto`에 정의된 전체 API다.

### 열거형 값

| 열거형 | 값 |
|--------|-----|
| `OrderSide` | `BUY` (매수), `SELL` (매도) |
| `OrderKind` | `MARKET` (시장가), `LIMIT` (지정가), `AFTER_HOURS_CLOSE` (장후 종가) |
| `OrderStatus` | `PENDING` (대기), `FILLED` (체결), `CANCELLED` (취소), `REJECTED` (거부) |

### RPC 목록

| RPC | 요청 | 응답 | 멱등성 키 필요 |
|-----|------|------|--------------|
| `GetBalance` | `user_id` | 잔고 정보 | 불필요 (읽기) |
| `ListOrders` | `user_id`, `status` (선택) | 주문 목록 | 불필요 (읽기) |
| `PlaceOrder` | `user_id`, `symbol`, `side`, `kind`, `quantity`, `price`, `command_metadata` | 생성된 주문 | **필요** |
| `CancelOrder` | `user_id`, `order_id`, `command_metadata` | 취소된 주문, 반환 금액 | **필요** |
| `AmendOrder` | `user_id`, `order_id`, `quantity`, `price`, `command_metadata` | 수정된 주문 | **필요** |

> `command_metadata.idempotency_key`는 클라이언트가 생성한 UUID다. 쓰기 RPC에는 반드시 포함해야 한다.  
> 같은 UUID를 `x-idempotency-key` gRPC 헤더에도 함께 보내야 한다.

### 예약 주문 RPC (미구현 예정)

`ListReservations`, `PlaceReservation`, `CancelReservation`, `AmendReservation`이 proto에 정의되어 있으나 현재 서비스에서는 아직 구현되지 않았다.

---

## 10. 핵심 설계 결정 요약

| 결정 | 이유 |
|------|------|
| REST 대신 gRPC | 서비스 간 통신에 적합. 계약이 명확하고, 이진 형식으로 빠르다 |
| 금액을 `long`(정수)으로 저장 | `double`/`float`의 부동소수점 오차가 금융에서 치명적이다 |
| 멱등성 키 강제 | 네트워크 오류로 인한 중복 주문을 방지한다 |
| Outbox 패턴 | DB 저장과 Kafka 발행의 원자성을 보장한다. 중간에 서버가 죽어도 데이터가 유실되지 않는다 |
| `public setter` 금지 | 상태 변경을 행위 메서드로 제한해 잘못된 상태 전이를 방지한다 |
| 도메인 서비스 분리 | gRPC 계층(요청/응답 형식)과 비즈니스 로직을 분리해 각각 독립적으로 테스트할 수 있다 |
| 트랜잭션 범위 | 도메인 변경 + outbox + 멱등성 레코드가 하나의 트랜잭션으로 묶여 일부만 성공하는 상황이 없다 |
| 초기 잔고 자동 생성 | 테스트 목적으로 새 사용자에게 1,000만원을 자동 지급한다. 운영 환경에서는 별도 입금 절차로 대체한다 |
