이 문서는 Candle 서비스의 기본 구현 규칙이다. 특별한 이유가 없으면 이 규칙을 따른다. 예외가 필요하면 변경 설명에 이유와 대안을 짧게 남긴다.

## 1. 모듈 경계

- `services:<name>-service`는 각각 독립 배포 단위다. 다른 서비스의 Java 코드, Entity, Repository, DB 테이블을 직접 참조하지 않는다.
- 서비스 간 동기 통신은 gRPC, 비동기 통신은 Kafka 이벤트를 사용한다. REST Controller는 만들지 않는다. 단, `auth-service`는 Gateway가 직접 라우팅하는 OAuth/JWT HTTP 서비스이므로 이 규칙의 예외다.
- `common`은 배포하지 않는 Java 라이브러리다. 공통 오류 계약, 이벤트 봉투, 범용 값 객체, 공통 유틸리티만 둔다.
- `common`에 서비스 도메인(`Order`, `User`, `Portfolio`), JPA Entity, Repository, 서비스별 protobuf DTO, 서비스별 오류 코드를 넣지 않는다. 공통 모듈이 모든 서비스의 도메인을 알아서는 안 된다.
- 외부 gRPC 계약은 proto 파일을 단일 소유 서비스가 관리한다. 다른 서비스는 생성된 클라이언트 계약만 의존한다. 계약의 호환성이 깨질 때 새 RPC/필드를 추가하고 기존 필드 번호와 의미는 변경하지 않는다.
- 새 의존성은 필요한 서비스에만 추가한다. DB·Kafka·Security 라이브러리를 편의상 전체 서비스에 넣지 않는다.

## 2. 기본 패키지 레이아웃

최상위 패키지는 레이어가 아니라 **도메인 이름**으로 나눈다. 팀원은 `order` 관련 코드를 찾을 때 `order` 아래만 보면 되며, 각 도메인 내부에서 `grpc`, `service`, `repository` 레이어를 찾는다.

```
org.profit.candle.trading
├── account/
│   ├── grpc             # Account gRPC endpoint, proto ↔ DTO 변환
│   ├── service          # AccountService, 구현체, 트랜잭션
│   ├── repository       # Account Reader/Writer와 JPA 구현
│   ├── entity           # Account, CashLedger, 값 객체
│   ├── dto              # AccountCommand, AccountResult
│   ├── event            # Account 관련 outbox event/publisher/consumer
│   └── exception        # AccountErrorCode, AccountException
├── order/
│   ├── grpc
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   ├── event
│   └── exception
├── reservation/         # 동일한 내부 구조
├── holding/             # 동일한 내부 구조
├── client/              # 여러 도메인이 공유하는 외부 서비스/증권사 client
├── config/              # Spring, gRPC, 보안 설정
└── support/             # trace, interceptor 등 서비스 내부 기술 공통 요소
```

- 단일 도메인에 `grpc` endpoint가 없으면 해당 패키지를 만들지 않는다. 모든 하위 폴더를 미리 생성하지 않는다.
- `grpc`는 REST의 Controller와 같은 진입점이다. gRPC 요청 파싱, 인증 주체 추출, 입력 검증, service 호출, protobuf 응답 변환만 한다.
- `service`는 업무 흐름, 권한 검사, 트랜잭션, Repository/client/event publisher 조합을 담당한다.
- `repository`는 영속화만 담당한다. 업무 판단, 이벤트 발행, 다른 서비스 호출을 넣지 않는다.
- `entity`는 자신의 상태와 불변식을 지킨다. 공개 setter를 만들지 않는다.
- 도메인 하나에서만 쓰는 client, event, exception은 반드시 그 도메인 아래에 둔다. 여러 도메인이 공유하는 기술 요소만 최상위 `client` 또는 `support`에 둔다.
- `util`, `common`, `manager`, `helper` 같은 포괄적 서비스 내부 패키지는 만들지 않는다. 실제 책임 이름을 쓴다.

## 3. 호출 방향과 트랜잭션

```
gRPC endpoint → Service → Repository / Client / Event publisher
                    ↓
                  Entity
```

- gRPC endpoint가 Repository를 직접 호출하지 않는다.
- Entity를 protobuf 응답으로 직접 반환하지 않는다. endpoint에서 service result를 protobuf message로 변환한다.
- `@Transactional`은 기본적으로 service의 public 메서드에 둔다. 조회는 `@Transactional(readOnly = true)`를 명시한다.
- Service는 다른 Service 구현체에 의존하지 않고 필요한 인터페이스에 의존한다. 순환 의존성은 설계 오류로 본다.
- DB 변경과 Kafka 발행이 함께 있으면 outbox를 사용한다. 트랜잭션 안에서 DB 저장 후 Kafka에 직접 전송하지 않는다.

## 4. 인터페이스 분리와 구현체 이름

인터페이스는 적극적으로 사용한다. 다만 큰 인터페이스 하나를 모든 구현체에 강요하지 않는다.

### 기본 원칙

- service, repository, client, event publisher는 인터페이스를 먼저 정의한다.
- 인터페이스는 **사용하는 쪽의 필요한 기능 단위**로 작게 만든다. 구현체가 지원하지 않는 메서드에서 `UnsupportedOperationException`을 던져야 한다면 이미 분리가 필요한 상태다.
- 읽기와 쓰기 요구가 다르거나, 구현체별 지원 범위가 다르면 즉시 분리한다. 예: `OrderReader` / `OrderWriter`, `MarketQuoteReader` / `MarketQuoteSubscriber`.
- 한 구현체가 여러 작은 인터페이스를 구현하는 것은 허용한다. 소비자는 필요한 인터페이스 하나만 주입받는다.
- 테스트 편의만을 이유로 의미 없는 인터페이스를 만들지는 않는다. 인터페이스 이름만 다른 `Foo`와 `FooImpl`은 금지한다.

| 역할 | 인터페이스 예시 | 구현체 예시 |
| --- | --- | --- |
| 업무 서비스 | `OrderService` | `OrderServiceImpl` 대신 `DefaultOrderService` |
| 조회 저장소 | `OrderReader` | `JpaOrderReader` |
| 저장 저장소 | `OrderWriter` | `JpaOrderWriter` |
| 외부 시세 호출 | `MarketQuoteClient` | `BrokerMarketQuoteClient` |
| 이벤트 발행 | `TradeEventPublisher` | `KafkaTradeEventPublisher` |
| 정책 | `FeePolicy` | `PercentageFeePolicy` |
- 구현체에 `Impl` 접미사는 사용하지 않는다. `Jpa`, `Redis`, `Kafka`, `Default`, `Cached`처럼 구현 방식 또는 역할을 나타낸다.
- 서비스 인터페이스가 단일 책임이 아니면 `OrderService`를 계속 키우지 말고 `OrderPlacementService`, `OrderCancellationService`, `OrderQueryService`로 분리한다.
- 의존성 주입은 생성자 주입만 사용한다. 필드 주입은 금지한다.

## 5. 이름과 메서드 규칙

- 클래스/enum/record는 `PascalCase`, 메서드·변수·패키지는 `camelCase`, 상수는 `UPPER_SNAKE_CASE`를 사용한다.
- 타입은 명사, 메서드는 동사로 이름 짓는다. `OrderService.placeOrder()`처럼 쓴다.
- 업무 행위에는 CRUD 관성 이름보다 의도를 쓴다. `createOrder()`보다 `placeOrder()`, `updateStatus()`보다 `cancel()` 또는 `approve()`를 사용한다.
- `find...`는 결과가 없을 수 있을 때만 사용하고 `Optional`을 반환한다. 반드시 있어야 하면 `get...` 또는 `load...`를 사용하고 서비스 오류를 던진다.
- 컬렉션은 절대 `null`을 반환하지 않는다. `findAllBy...`, `list...`를 사용한다.
- boolean 필드와 상태 조회 메서드는 `deleted`, `active`, `expired`처럼 상태 자체로 이름 짓는다. `isDeleted`라는 필드명은 쓰지 않는다. Entity의 상태 조회도 `deleted()`를 우선 사용한다.
- 가능 여부를 묻는 행동 메서드만 `can...`, `has...`, `should...`를 쓴다. 예: `order.canCancel()`.
- 변환 메서드는 `toProto`, `fromProto`, `toResult`, `from`처럼 입력과 출력을 드러낸다. `process`, `handle`, `doSomething`은 금지한다.
- 메서드 인자가 4개를 넘으면 `Command`, `Criteria`, 값 객체로 묶는다.

## 6. Entity, DTO, 값 객체

- JPA Entity는 `entity` 패키지에 둔다. gRPC protobuf message나 repository projection을 Entity와 겸용하지 않는다.
- Entity의 기본 생성자는 반드시 `protected`로 둔다.

```java
@Entity
@NoArgsConstructor(access = PROTECTED)
public class Order {
    protected Order() {
    }

    public Order(AccountId accountId, Money price, Quantity quantity) {
        this.price = valdidatePrice(price);
    }
    
    public Money valdidatePrice(Money price) {
	    if ( ~~ ) {
			  return price;  
    }
    throw new 
    }
    
    this.price = 0
}
```

- Entity에 public setter를 두지 않는다. 상태 변경은 `cancel()`, `execute(executedQuantity)` 같은 의미 있는 메서드로만 한다.
- gRPC endpoint 입력/출력은 proto message, service 입력은 `Command`, service 출력은 `Result`, 검색 조건은 `Criteria`를 사용한다.
- 단순 불변 전달 객체는 Java `record`를 우선 사용한다.
- 금액은 `BigDecimal` 또는 최소 화폐 단위 `long`만 사용한다. `double`/`float`는 금지한다.
- 종목 코드, 금액, 수량, 계좌 ID처럼 규칙이 있는 값은 값 객체로 승격한다.
- 서버 내부 시각은 `Instant`를 기본으로 한다. 거래일처럼 시간대가 필요한 경우에만 기준 시간대를 명시한 `LocalDate`/`ZonedDateTime`을 쓴다.

## 7. Validation과 불변식

| 위치 | 책임 | 방법 |
| --- | --- | --- |
| gRPC endpoint | protobuf 필수값, 형식, 범위 | 명시적 validator 또는 proto validation 규칙 |
| service | 권한, 존재 여부, 중복 요청, 서비스 간 상태 | repository/client 조회 후 검사 |
| entity | 항상 유지되어야 하는 상태 규칙 | 생성자와 행위 메서드에서 검사 |
| DB | 최후의 무결성 | NOT NULL, UNIQUE, FK, CHECK, lock |
- gRPC는 REST의 `@Valid`를 전제로 하지 않는다. proto validation 도입 전에는 endpoint에서 명시적으로 검증하고 service/Entity에서 핵심 규칙을 다시 보장한다.
- Request 단계의 형식 검증과 Entity 불변식을 혼동하지 않는다. Kafka consumer나 batch도 service로 들어올 수 있으므로 Entity 불변식은 항상 유지되어야 한다.
- 동시성 문제는 사전 조회만으로 해결하지 않는다. 중복 주문·잔고 차감에는 DB 제약, 낙관/비관 락, idempotency key를 함께 설계한다.

## 8. 예외, 오류 코드, gRPC 메시지

### 별도 예외를 만드는 경우

다음 중 하나라면 별도 예외 타입 또는 명확한 `ErrorCode`를 만든다.

- 호출자에게 다른 gRPC status 또는 다른 복구 방법을 제공해야 한다.
- 도메인 용어로 오류를 명확히 표현해야 한다. 예: `InsufficientBalanceException`.
- 재시도 여부, 알림, 모니터링 지표가 달라진다.
- 오류가 업무 규칙을 설명하며 테스트에서 독립적으로 검증할 가치가 있다.

같은 status·같은 코드·같은 처리인 단순 오류는 예외 클래스를 계속 만들지 말고 `CandleException(ErrorCode)`로 표현한다.

### common과 서비스의 소유권

```
common
├── ErrorCode                   # code(), message() 공통 계약
└── CandleException              # ErrorCode와 cause를 보유하는 기본 RuntimeException

<service>
├── <Service>ErrorCode           # 서비스 소유 코드·노출 메시지·상태 매핑
├── <Service>Exception           # CandleException 확장
└── api/grpc exception handler   # transport별 응답 변환
```

- `common`은 오류의 공통 **형태**만 소유한다. `COMMON_` 계열 기술 오류 외에 `AUTH_`, `TRADING_` 같은 업무 코드와 한국어 메시지는 해당 서비스가 소유한다.
- 사용자 메시지는 서비스별 `<Service>ErrorCode.message()` 한 곳에서 결정한다. Entity, service, client, endpoint에 오류 문자열 리터럴을 넣지 않는다.
- `<Service>ErrorCode`는 코드와 메시지를 반드시 구현한다. HTTP를 직접 노출하는 서비스는 HTTP 상태도 enum에 두고, gRPC 서비스는 handler에서 gRPC status로 매핑한다. common에 HTTP/gRPC 의존성을 추가하지 않는다.
- 오류를 감쌀 때는 원인을 보존한다: `throw new AuthException(AuthErrorCode.GOOGLE_OAUTH_EXCHANGE_FAILED, cause)`. 내부 원인, SQL, token, 계좌번호 전체, 외부 API 원문은 응답 메시지에 넣지 않는다.
- 예상 가능한 업무 오류는 `WARN` 또는 `INFO`, 예상하지 못한 오류는 `ERROR`로 한 번만 기록한다. 서비스별 handler/interceptor가 최종 변환과 로깅을 담당한다.
- 5XX 대 에러 발생시 400 에러로 내려준다. 500 에러의 본문을 사용자에게 반환하지 않는다.

### 구현 예시

```java
public enum AuthErrorCode implements ErrorCode {
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED,
            "AUTH_INVALID_REFRESH_TOKEN", "Refresh token이 유효하지 않습니다.");
    // code(), message(), httpStatus()
}

public class AuthException extends CandleException {
    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }
}
```

```java
// service/client: 메시지를 직접 만들지 않는다.
throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);

// HTTP endpoint: @RestControllerAdvice에서만 JSON 응답으로 변환한다.
return ResponseEntity.status(exception.errorCode().httpStatus())
        .body(new ErrorResponse(exception.errorCode().code(), exception.errorCode().message()));
```

- `auth-service`는 Gateway가 직접 라우팅하는 HTTP 서비스이므로 `@RestControllerAdvice`로 상태를 변환한다.
- gRPC 서비스는 같은 `CandleException` 구조를 사용하되 gRPC exception interceptor/advice에서 `StatusRuntimeException`으로 변환한다. 가능하면 `google.rpc.ErrorInfo.reason`에 ErrorCode를, metadata에 `traceId`를 넣는다.
- 기술 오류를 업무 오류 코드로 무조건 감싸지 않는다. 호출자가 처리할 수 없는 예상 밖 오류는 공통 내부 오류 응답으로 변환하고 원인은 로그에만 남긴다.

## 9. gRPC와 Kafka 계약

- proto package, service, RPC 이름은 도메인을 드러낸다. 예: `TradingService.PlaceOrder`, `PortfolioService.GetPortfolio`.
- gRPC 요청/응답에는 Entity를 노출하지 않는다. endpoint에서 Command/Result와 protobuf를 변환한다.
- 목록 RPC는 page size 최대값, page token, 정렬 가능한 필드를 명시한다.
- 클라이언트가 모든 쓰기 요청에 `idempotency_key`를 제공한다. 서버는 `accountId + operation + idempotencyKey` 기준으로 처리 결과를 저장한다.
- 클라이언트는 본문 hash가 아닌 난수 기반 opaque key를 보내고, canonical request hash는 반드시 소유 서비스가 계산한다. 상세 규칙은 IDEMPOTENCY.md를 따른다.
- 동일 key로 같은 요청을 다시 받으면 이전 성공/실패 결과를 재현한다. 동일 key에 다른 payload가 오면 `ALREADY_EXISTS` 또는 `INVALID_ARGUMENT`으로 거절한다.
- idempotency 레코드는 업무 트랜잭션과 같은 DB 트랜잭션에 기록하며, 보관 기간과 정리 작업을 서비스별로 명시한다.
- Kafka 이벤트는 과거형을 사용한다: `TradeExecuted`, `MissionAchieved`. command를 event로 위장하지 않는다.
- 이벤트에는 event ID, type, aggregate ID, occurredAt, schema version, payload를 둔다. 필드 번호/기존 필드 의미는 변경하지 않는다.

### Outbox는 기본 선택이다

- DB 상태 변경과 Kafka 발행이 함께 일어나는 서비스는 outbox 테이블에 이벤트를 같은 트랜잭션으로 저장한다.
- 별도 publisher가 미발행 outbox를 Kafka로 전송하고, 성공 후 발행 완료 처리한다. 재시도로 인한 중복은 발생할 수 있으므로 consumer는 event ID 기준으로 멱등 처리한다.
- Kafka 전송 실패를 이유로 이미 커밋된 업무 데이터를 롤백하려 하지 않는다. outbox 재시도와 모니터링으로 복구한다.
- outbox backlog, publish failure, oldest unpublished event age를 메트릭과 알람 대상으로 둔다.

## 10. AOP 도입 기준

AOP는 비즈니스 흐름을 숨기는 수단이 아니다. 다음을 만족할 때만 도입을 검토한다.

- 동일한 기술적 관심사가 최소 3곳 이상에서 반복된다.
- 적용 범위가 annotation 또는 package로 명확히 제한된다.
- 순서, 실패 시 동작, 성능 비용을 테스트로 확인할 수 있다.

적합한 용도는 request/trace logging, metric 수집, audit, 공통 권한 검사, idempotency 경계, retry 후보 감지다. `@Transactional`도 프레임워크 AOP이므로 self-invocation 문제를 인지한다.

- 주문 승인, 잔고 차감, 수수료 계산처럼 업무 순서가 중요한 로직은 AOP에 넣지 않고 service에 명시한다.
- AOP로 retry할 때는 읽기나 명확히 멱등한 작업만 자동 재시도한다. 주문/이체 같은 쓰기 요청은 idempotency와 함께 설계되지 않으면 재시도하지 않는다.
- Aspect는 `@Order`를 명시하고, 예외 변환·트랜잭션·로깅의 순서를 문서화한다.
- AOP 도입 전에는 중복 코드가 실제로 유지보수 비용인지 먼저 확인한다. 두 번의 중복은 허용하고, 세 번째 반복부터 후보로 검토한다.

## 11. Java / Spring / JPA 규칙

- Java 21을 기준으로 한다. 불변 전달 객체는 `record`를 우선 사용한다.
- import wildcard와 사용하지 않는 import는 금지한다.
- `Optional`은 반환 타입에서만 사용한다. 필드, 메서드 인자, JPA Entity 필드에서 사용하지 않으며 `optional.get()`은 금지한다.
- 정상 흐름 제어에 `null`을 쓰지 않는다. 빈 컬렉션, Optional, 명시적인 결과 타입을 쓴다.
- static utility class는 `final`로 선언하고 기본 생성자를 반드시 막는다.

```java
public final class MoneyCalculator {
    private MoneyCalculator() {
        throw new AssertionError("Utility class");
    }
}
```

- static utility는 순수 변환/계산에만 쓴다. 시간, I/O, 설정, 외부 호출이 있으면 주입 가능한 클래스로 만든다.
- `catch (Exception)`은 최상위 gRPC 예외 변환 또는 명확한 경계 번역에서만 사용한다. 빈 catch는 금지한다.
- `@ConfigurationProperties`로 설정을 묶는다. 비밀값을 코드, proto 기본값, application 설정 파일에 넣지 않는다.
- JPA N+1, bulk update 뒤 영속성 컨텍스트, 락 전략을 명시적으로 검토한다.
- 외부 호출에는 connect/read timeout과 재시도 조건을 정의한다. 쓰기 호출은 자동 재시도하지 않는다.

## 12. 테스트와 관측성

- Entity 규칙은 단위 테스트, service 흐름은 repository/client fake 또는 mock 테스트, repository·gRPC·Kafka·outbox는 통합 테스트로 검증한다.
- 테스트 이름은 결과를 드러낸다: `shouldRejectOrderWhenAvailableBalanceIsInsufficient`.
- 시간, UUID, 외부 호출은 테스트에서 제어 가능하게 주입한다.
- 정상 흐름뿐 아니라 입력 오류, 권한, 중복 idempotency key, 동시성, outbox 발행 실패와 중복 소비를 테스트한다.
- 로그에는 `traceId`, 필요한 범위의 `userId`, `orderId` 같은 추적 키를 구조화해 기록한다. 민감 정보는 마스킹한다.
- 메트릭 기본 후보: gRPC 요청 수/상태/지연 시간, Kafka consumer lag, outbox backlog·실패·지연, 외부 호출 실패율. 사용자 ID·주문 ID를 metric label로 쓰지 않는다.

## 13. 페이징

- 목록 gRPC는 데이터가 적어 보여도 처음부터 `page_size`와 `page_token`을 계약에 둔다. 전체 목록 반환은 관리자 전용·명시적 제한이 있는 경우만 허용한다.
- 공통 proto의 `PageRequest { page_size, page_token }`, `PageResponse { next_page_token }`를 사용한다. `page_size`의 기본값과 최대값은 RPC마다 문서화하고 서버에서 강제한다.
- 기본 정렬은 반드시 안정적이어야 한다. 예: `created_at DESC, id DESC`. 정렬 키가 같을 때 고유 ID를 보조 키로 포함한다.
- 대량·변경 빈도가 높은 목록은 offset보다 cursor/page token 방식을 사용한다. 토큰에는 마지막 정렬 키와 ID만 인코딩하고, 사용자 권한·필터·정렬 조건과 맞지 않는 토큰은 거절한다.
- 외부 호출 어댑터는 프론트의 `page`, `cursor` 형식을 내부 gRPC `page_token`으로 변환할 수 있지만, 서비스 내부의 정렬/커서 계약을 임의로 재구성하지 않는다.
- `repeated` 결과에는 최대 반환 건수를 둔다. 서버 스트리밍은 대용량 단방향 전달에만 사용하며, 일반 화면 목록을 위한 페이징 대체 수단으로 남용하지 않는다.

## 14. DB 인덱스

인덱스는 “자주 실행되는 실제 조회 조건과 정렬”에 맞춰 추가한다. 막연히 외래 키나 모든 컬럼에 인덱스를 붙이지 않는다.

- `WHERE`, `JOIN`, `ORDER BY`, cursor pagination의 정렬 키, unique/idempotency 조회가 느리거나 핵심 경로라면 인덱스 후보로 검토한다.
- 복합 인덱스는 동등 조건을 앞에, 범위 조건과 정렬 키를 뒤에 둔다. 예: 사용자 주문 목록은 `(user_id, status, created_at DESC, id DESC)`를 검토한다.
- soft delete 테이블은 활성 행 조회가 대부분이면 `WHERE deleted = false` 부분 인덱스를 검토한다.
- outbox는 미발행 이벤트 폴링 조건에 맞는 부분 인덱스를 둔다. 예: `WHERE published_at IS NULL`과 `occurred_at` 순서.
- idempotency 테이블에는 `(user_id, operation, idempotency_key)` unique index가 필요하다. 같은 키의 request hash와 저장된 응답도 함께 보관한다.
- unique 제약은 데이터 무결성 규칙이며 인덱스를 겸한다. 애플리케이션의 사전 조회만으로 중복을 막지 않는다.
- 인덱스 추가 전에는 `EXPLAIN (ANALYZE, BUFFERS)` 또는 운영 관측 데이터로 대상 쿼리를 확인한다. 추가 후 쓰기 비용·인덱스 크기·실행 계획 변화를 확인한다.
- 사용되지 않는 중복 인덱스는 관측 후 제거한다. 대용량 운영 테이블의 인덱스 생성/삭제는 락 영향을 검토하고 가능한 온라인 방식으로 수행한다.

## 15. DB 마이그레이션

- DB 스키마 변경은 서비스별로 버전 관리하고, 애플리케이션 시작 시 적용되는 migration으로만 변경한다. 수동 운영 DB 변경은 금지한다.
- migration 파일은 각 서비스의 `src/main/resources/migration` 아래에 둔다.
- 파일명은 날짜와 순번을 포함한 다음 형식을 사용한다: `VYYYYMMDD_NNN__lower_snake_case_description.sql`.

```
src/main/resources/migration/
├── V20260620_001__create_orders.sql
├── V20260620_002__add_orders_user_status_created_at_index.sql
└── V20260621_001__create_outbox_events.sql
```

- 이미 어느 환경에서든 적용된 migration 파일은 수정·삭제·이름 변경하지 않는다. 수정이 필요하면 새 forward migration을 추가한다.
- migration 하나는 독립적으로 이해 가능한 작은 변경으로 작성한다. 대규모 backfill, 대량 인덱스 생성, 컬럼 제거는 별도 migration과 운영 절차로 나눈다.
- 삭제/이름 변경은 확장-전환-축소 순서를 따른다: 새 컬럼 추가 → 코드 양쪽 호환 → 데이터 이전 → 구 코드 제거 → 이후 migration에서 기존 컬럼 제거.
- 모든 DDL에는 필요한 index, unique constraint, FK, nullability, 기본값을 함께 검토한다. Entity 자동 DDL 생성은 local 이외 환경에서 사용하지 않는다.

## 16. Proto 계약 관리

- proto는 모노레포 루트 `proto/`에 중앙 관리한다. 서비스 Java 소스 디렉터리나 `common` Java 모듈에 흩어 두지 않는다.

```
proto/
├── buf.yaml
├── buf.gen.yaml
└── candle/
    ├── common/v1/common.proto
    ├── auth/v1/auth.proto
    ├── user/v1/user.proto
    ├── market/v1/market.proto
    ├── trading/v1/trading.proto
    ├── portfolio/v1/portfolio.proto
    ├── mission/v1/mission.proto
    ├── ranking/v1/ranking.proto
    ├── learning/v1/learning.proto
    └── notification/v1/notification.proto
```

- 각 proto는 해당 서비스가 소유하고 승인한다. `common/v1`에는 Page, Money, Timestamp 기반 Audit, 공통 ErrorDetail처럼 진짜 공통 메시지만 둔다.
- package는 `candle.<service>.v1` 형식으로 한다. enum에는 항상 `_UNSPECIFIED = 0`을 둔다.
- field number는 영구 식별자다. 배포된 field/RPC/enum 값은 번호·이름·의미를 변경하거나 재사용하지 않는다. 제거 시 `reserved`로 번호와 이름을 예약한다.
- 호환성이 깨지는 변경은 `v2` 패키지와 새 RPC/message로 추가한다. 선택 필드 추가와 새 enum 값 추가를 기본 진화 방식으로 한다.
- CI에서 `buf lint`와 이전 기준선 대비 breaking check를 실행한다. Java gRPC stub은 proto에서 생성하며 수동 수정하지 않는다.
- 외부 호출 어댑터는 서비스의 생성된 gRPC client만 사용한다. 외부 REST DTO/TypeBox 스키마와 내부 proto를 공유 DTO로 강제하지 않고 어댑터에서 명시적으로 변환한다.

## 17. 변경 전 체크리스트

- 이 코드는 어느 서비스가 소유하는가? 다른 서비스의 Entity/DB에 결합되어 있지 않은가?
- 인터페이스는 소비자가 필요한 기능만 노출하는가? 구현체에 미지원 메서드가 남아 있지 않은가?
- gRPC endpoint, service, repository의 책임이 섞이지 않았는가?
- Entity 기본 생성자는 `protected`이고, setter 대신 행위 메서드로 불변식을 지키는가?
- 오류 코드/메시지 소유권이 명확하며 민감 정보가 노출되지 않는가?
- 모든 쓰기 RPC가 client-provided idempotency key와 outbox를 고려하는가?
- AOP가 업무 흐름을 숨기지 않고, 적용 순서와 실패 동작이 검증 가능한가?
- static utility class의 생성자가 막혀 있는가?
- 목록 API가 안정적인 정렬, 최대 page size, page token을 갖는가?
- 실제 쿼리 조건에 근거한 index와 migration이 함께 추가되었는가?
- proto 변경이 하위 호환이며 소유 서비스의 검토와 buf 검사를 통과하는가?