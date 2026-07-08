# Wishlist Service 구조 설명

![Wishlist architecture overview](./assets/wishlist-architecture-overview.svg)

이 문서는 `wishlist-service`의 구조와 동작 방식을 설명한다.
개발 경험이나 gRPC 지식 없이도 읽을 수 있도록 작성했다.



---

## 목차

1. [Wishlist Service가 하는 일](#1-wishlist-service가-하는-일)
2. [이 서비스가 쓰는 통신 방식들](#2-이-서비스가-쓰는-통신-방식들)
3. [서비스 구조 한눈에 보기](#3-서비스-구조-한눈에-보기)
4. [계층별 역할 설명](#4-계층별-역할-설명)
5. [주요 기능 흐름](#5-주요-기능-흐름)
6. [멱등성은 어떻게 처리하나](#6-멱등성은-어떻게-처리하나)
7. [Outbox 패턴 — 이 서비스는 무엇을 내보내나](#7-outbox-패턴--이-서비스는-무엇을-내보내나)
8. [데이터베이스 테이블 설명](#8-데이터베이스-테이블-설명)
9. [API 계약](#9-api-계약)
10. [핵심 설계 결정 요약](#10-핵심-설계-결정-요약)
11. [테스트는 어떻게 검증하나](#11-테스트는-어떻게-검증하나)

---

## 1. Wishlist Service가 하는 일

Wishlist Service는 사용자의 **관심종목**을 관리하고, 관심종목의 가격이 크게 움직이면 **알림**을 보내는 서비스다.

| 기능 | 설명 |
|------|------|
| 관심종목 등록 | 사용자가 특정 종목을 관심종목으로 등록한다 |
| 관심종목 삭제 | 등록을 해제한다(완전히 지우지 않고 "삭제됨" 표시만 한다 — 6절 참고) |
| 관심종목 목록 조회 | 내가 등록한 관심종목 목록을 페이지 단위로 조회한다 |
| 급등락 감지 | 실시간 시세를 받아, 시가 대비 얼마나 올랐는지/내렸는지 계산한다 |
| 급등락 알림 | 임계치를 넘으면 그 종목을 관심등록한 사람들에게 알림을 보낸다 |
| 실시간 시세 구독 요청/해제 | 관심 유저가 0명이 되거나 1명이 처음 생기면, market-service에 "이 종목 실시간 시세 구독을 켜줘/꺼줘"라고 알려준다 |

마지막 항목이 이 서비스의 특이한 지점이다 — **관심종목 등록 자체가 다른 서비스(market-service)의 동작을 결정한다.** 아무도 관심 없는 종목의 시세를 실시간으로 받아올 필요가 없기 때문에, 이 서비스가 "언제 구독을 켜고 끌지"를 사실상 결정하는 역할을 겸한다.

---

## 2. 이 서비스가 쓰는 통신 방식들

다른 서비스의 예를 들면, Trading Service는 gRPC 하나만 썼고, Chatting Service는 WebSocket 하나만 썼다. 이 서비스는 **네 가지**를 동시에 쓴다 — 그만큼 이 서비스가 여러 시스템의 "중간 다리" 역할을 한다는 뜻이다.

```
① gRPC 서버   : BFF가 이 서비스에게 "관심종목 등록해줘" (Trading Service와 동일한 방식)
② gRPC 클라이언트 : 이 서비스가 notification-service에게 "알림 보내줘"
③ Redis 구독  : market-service가 흘려보내는 실시간 시세를 받는다 (Chatting Service와 동일한 Pub/Sub 방식)
④ Kafka 발행  : 이 서비스가 market-service에게 "이 종목 구독 켜줘/꺼줘" (Trading Service의 Outbox와 동일한 패턴)
```

```
                    ┌─────────────────┐
   BFF ──①gRPC──▶  │                 │ ──②gRPC──▶ notification-service
                    │ wishlist-service│
market-service ─③Redis▶│                 │ ──④Kafka──▶ market-service
                    └─────────────────┘
```

같은 market-service와도 **양방향으로 다른 통신 수단**을 쓴다는 점이 흥미롭다 — market-service가 이 서비스에 시세를 줄 때는 Redis Pub/Sub(빠르고 가벼움, 유실 감수), 이 서비스가 market-service에 구독 요청을 보낼 때는 Kafka(느리더라도 반드시 전달되어야 함)를 쓴다. 왜 이렇게 나눴는지는 6~7절에서 설명한다.

---

## 3. 서비스 구조 한눈에 보기

```
wishlist-service/src/main/java/org/profit/candle/wishlist/
│
├── wishlist/                    ← "관심종목이 뭔지" 관리하는 도메인
│   ├── entity/          WishlistItem                   (soft delete: deleted_at)
│   ├── dto/              AddWishlistItemCommand, RemoveWishlistItemCommand,
│   │                     WishlistItemResult, ListWishlistItemsResult
│   ├── service/           WishlistService, DefaultWishlistService
│   ├── grpc/              WishlistGrpcService           ← gRPC 진입점
│   ├── repository/       WishlistItemReader, WishlistItemWriter, ...
│   └── exception/         WishlistErrorCode, WishlistException
│
├── alert/                       ← "가격이 얼마나 움직였는지" 판단하고 알림을 만드는 도메인
│   ├── entity/            WishlistPriceAlert, MarketOpenSnapshot, AlertDirection
│   ├── dto/               PriceAlertCandidate
│   ├── service/
│   │   ├── PriceAlertService          ← 이 서비스의 두뇌 — 시세 평가부터 알림 발송까지 오케스트레이션
│   │   └── PriceChangeCalculator      ← 순수 계산기(시가 대비 등락률)
│   └── repository/       PriceAlertReader, PriceAlertWriter, ...
│
├── market/                       ← "실시간 시세를 어떻게 받을지"만 담당
│   ├── dto/                QuoteTick, QuoteMessageParser
│   └── redis/               RedisMarketQuoteSubscriber   ← Redis 채널 구독자
│
├── notification/                 ← "알림을 어떻게 보낼지"만 담당
│   └── client/               NotificationClient, GrpcNotificationClient
│
├── event/                         ← "구독 수요를 어떻게 알릴지"만 담당 (Outbox → Kafka)
│   ├── WishlistEventType, WishlistSymbolEvent
│   ├── OutboxWriter, OutboxEventRepository
│   └── KafkaOutboxPublisher       ← @Scheduled 폴링 발행자(Trading Service와 동일한 패턴)
│
└── config/
    ├── WishlistProperties          ← alert(threshold/retry), market(quoteChannel), notification(address/deadline)
    └── RedisListenerConfig          ← Redis 시세 채널 구독 컨테이너 등록
```

`wishlist` 패키지와 `alert` 패키지가 왜 나뉘어 있는지 눈여겨보자 — "무엇을 관심등록했는지"와 "그 종목 가격이 얼마나 움직였는지"는 서로 다른 책임이고, 실제로 `alert` 쪽 로직은 `wishlist` 쪽 데이터를 **읽기만** 한다(4-2절).

---

## 4. 계층별 역할 설명

### 4-1. 관심종목 등록/삭제 (`wishlist/service/DefaultWishlistService.java`)

**비유**: 즐겨찾기 버튼. 이미 즐겨찾기한 걸 다시 누르면 "또 추가"되는 게 아니라 그냥 갱신되는 것과 같다.

```java
// DefaultWishlistService.java (일부)
public WishlistItemResult add(AddWishlistItemCommand command) {
    String symbol = normalizeSymbol(command.symbol());
    WishlistItem existing = reader.findActive(command.userId(), symbol).orElse(null);
    if (existing != null) {
        existing.updateSnapshot(command.displayName(), command.market(), now);  // 이미 있으면 갱신만
        return toResult(writer.save(existing));
    }

    WishlistItem saved = writer.save(WishlistItem.add(command.userId(), symbol, ...));
    // 이 심볼의 활성 관심 유저가 0→1 로 늘었으면 실시간 구독 수요 이벤트를 발행한다.
    if (reader.listActiveBySymbol(symbol).size() == 1) {
        outboxWriter.recordSymbolActivated(symbol);
    }
    return toResult(saved);
}
```

"이미 등록돼 있으면 새로 안 만들고 갱신만 한다"는 이 패턴이 6절에서 설명할 멱등성 처리와 직결된다.

### 4-2. 시세 평가와 알림 (`alert/service/PriceAlertService.java`)

**비유**: 이 서비스의 심장 박동기. 시세 신호(Redis)가 올 때마다 뛰면서, "이 정도면 알려야 하나?"를 판단하고, 필요하면 알림을 내보낸다.

```java
// PriceAlertService.java (일부, 흐름만 발췌)
public void evaluate(QuoteTick tick) {
    if (tick == null || !tick.open()) return;   // 장 마감 등이면 평가 안 함

    int changeBasisPoints = PriceChangeCalculator.basisPoints(tick.openPrice(), tick.price());
    AlertDirection direction = PriceChangeCalculator.direction(changeBasisPoints, threshold);

    if (direction == null) {
        observeSnapshot(...);   // 임계치 미달이면 "현재가 스냅샷"만 갱신하고 끝
        return;
    }

    List<PriceAlertCandidate> candidates = prepareCandidates(...);  // 임계치 초과 → 알림 후보 생성
    candidates.forEach(this::sendNotification);   // 트랜잭션 밖에서 실제 발송 시도
}
```

여기서 중요한 설계: **"DB에 알림 후보를 저장하는 것"과 "실제로 notification-service에 발송하는 것"이 서로 다른 트랜잭션**이다. 발송이 실패해도(notification-service가 잠깐 죽어도) 이미 DB에 남긴 alert 레코드는 사라지지 않는다 — `notification_id`가 비어있는 채로 남아서, 재시도 스케줄러가 나중에 다시 시도한다.

```java
// PriceAlertService.java — 재시도 스케줄러
@Scheduled(fixedDelayString = "${wishlist.alert.retry-delay:PT30S}")
public void retryPendingNotifications() {
    // notification_id가 NULL인 alert만 찾아서 다시 발송 시도
}
```

### 4-3. 실시간 시세 수신 (`market/redis/RedisMarketQuoteSubscriber.java`)

**비유**: 라디오 수신기. market-service가 전파를 쏘면(Redis Pub/Sub), 이 클래스가 받아서 다음 담당자(`PriceAlertService`)에게 넘겨준다.

```java
// RedisMarketQuoteSubscriber.java
public void onMessage(Message message, byte[] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
        priceAlertService.evaluate(parser.parse(payload));
    } catch (IllegalArgumentException e) {
        log.warn("Invalid market quote payload for wishlist alert");  // 잘못된 메시지 하나만 무시하고 계속 진행
    }
}
```

### 4-4. 알림 발송 (`notification/client/GrpcNotificationClient.java`)

**비유**: 우체국 창구. 알림 내용을 봉투에 담아(gRPC 요청 조립) notification-service라는 배달부에게 넘긴다.

```java
// GrpcNotificationClient.java (일부)
CreateNotificationRequest request = CreateNotificationRequest.newBuilder()
        .setUserId(command.userId().toString())
        .setType(toProtoType(command.direction()))     // 상승/하락에 따라 알림 타입 분기
        .setTitle(title(command.direction()))           // "관심종목 급등" / "관심종목 급락"
        .setBody(body(command))                          // "005930이(가) 오늘 시가 대비 2.50% 상승했습니다."
        .setCommandMetadata(CommandMetadata.newBuilder()
                .setIdempotencyKey(command.idempotencyKey())   // 결정론적으로 만든 키(6절)
                .build())
        .build();
```

### 4-5. 구독 수요 이벤트 발행 (`event/KafkaOutboxPublisher.java`)

`@Scheduled`로 주기적(기본 5초)으로 미발행 이벤트를 찾아 Kafka로 보낸다.

```java
// KafkaOutboxPublisher.java
@Scheduled(fixedDelayString = "${wishlist.outbox.publish-interval-ms:5000}")
@Transactional
public void publishPendingEvents() {
    outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(event -> {
        kafkaTemplate.send(WishlistEventType.TOPIC, event.aggregateId(), event.payload()).join();
        event.markPublished(Instant.now());
    });
}
```

---

## 5. 주요 기능 흐름

### 5-1. 관심종목 등록 흐름 (`AddWishlistItem`)

```
클라이언트(BFF)
    │  AddWishlistItemRequest { userId, symbol, displayName, market, command_metadata }
    ▼
[WishlistGrpcService.addWishlistItem]
    │  userId 파싱, 멱등성 키 형식 검증(8~128자)
    ▼
[DefaultWishlistService.add]
    │
    ├─ 종목코드 정규화(trim + 대문자, 20자 이하)
    ├─ 이미 활성 등록이 있는지 확인
    │   ├─ 있음 → 표시명/시장 정보만 갱신(새로 안 만듦)
    │   └─ 없음 → 신규 저장
    │        └─ 이 종목의 활성 관심 유저 수가 "방금 1명이 됐는지" 확인
    │             └─ 맞으면 → "이 종목 구독 켜줘" 이벤트를 outbox에 기록(같은 트랜잭션)
    ▼
클라이언트에 AddWishlistItemResponse 반환
```

### 5-2. 실시간 시세 → 급등락 알림 흐름

```
market-service가 Redis 채널에 시세 발행 (예: 005930, 현재가 71,700원, 시가 70,000원)
    ▼
[RedisMarketQuoteSubscriber.onMessage]
    ▼
[PriceAlertService.evaluate]
    │
    ├─ 등락률 계산: (71,700 - 70,000) / 70,000 = +2.43%
    ├─ 설정된 임계치(기본 5%)와 비교
    │
    ├─ 임계치 미달 → 스냅샷만 갱신하고 종료
    │
    └─ 임계치 이상 → 급등/급락 확정
         ├─ 스냅샷 갱신(같은 트랜잭션)
         ├─ 이 종목을 관심등록한 유저 목록 조회
         ├─ 유저별로 "오늘 이미 같은 알림을 보냈는지" 확인 → 아니면 alert 레코드 insert
         │   (여기까지 하나의 트랜잭션)
         │
         └─ (트랜잭션 종료 후) 유저별로 notification-service에 실제 알림 발송 시도
              ├─ 성공 → alert 레코드에 notification_id 기록
              └─ 실패 → 그대로 두고 30초 뒤 재시도 스케줄러가 다시 시도
```

### 5-3. 알림 재시도 흐름

```
@Scheduled(30초 주기) PriceAlertService.retryPendingNotifications
    → notification_id가 비어있는 alert 레코드를 최대 N건 조회
    → 5-2절의 "발송 시도" 단계만 다시 실행
```

---

## 6. 멱등성은 어떻게 처리하나

여기는 `idempotency_records` 테이블이 **없다.** 대신 이 서비스는 **"애초에 같은 요청을 두 번 보내도 결과가 같게 설계"**하는 방식(자연 멱등성)을 쓴다.

### 관심종목 등록 — 자연 멱등성 + DB 유니크 인덱스

`AddWishlistItem`을 두 번 호출해도, 두 번째 호출은 "이미 있네, 갱신만 하자"가 되기 때문에 중복 데이터가 생기지 않는다(4-1절 코드 참고). `command_metadata.idempotency_key`는 gRPC 계층에서 **형식만** 검증하고 실제로는 저장하거나 재생하지 않는다.

동시에 두 요청이 경쟁하는 상황까지 대비해, DB에도 안전장치가 있다:

```sql
-- 활성 항목 기준으로 유저+종목 유일성을 강제 (동시 요청이 와도 중복 활성 레코드가 못 생김)
CREATE UNIQUE INDEX uk_wishlist_items_user_symbol_active
    ON wishlist_items(user_id, symbol) WHERE deleted_at IS NULL;
```

즉 "애플리케이션이 먼저 확인하고, DB가 마지막으로 한 번 더 막아주는" 이중 안전장치다.

### 알림 발송 — 결정론적 키 + 이중 유니크 제약

같은 유저가 같은 종목에 대해 같은 날 같은 방향으로 알림을 두 번 받으면 안 된다. 이건 애플리케이션 레벨 확인(`alertExists`)과 DB 제약(`uk_wishlist_price_alerts_once`) 두 겹으로 막는다. notification-service에 보내는 `idempotencyKey`도 매번 새로 만드는 UUID가 아니라, **같은 조건이면 항상 같은 문자열이 나오도록** 만든다:

```java
// PriceAlertService.java
private static String idempotencyKey(UUID userId, String symbol, LocalDate tradingDate,
                                     AlertDirection direction, int thresholdBasisPoints) {
    return "wishlist-price-alert:%s:%s:%s:%s:%d"
            .formatted(userId, symbol, tradingDate, direction.name(), thresholdBasisPoints);
}
```

---

## 7. Outbox 패턴 — 이 서비스는 무엇을 내보내나

Outbox 패턴 자체의 개념(DB 저장과 메시지 발행을 한 트랜잭션으로 묶어야 하는 이유)은 Trading Service 문서의 설명과 완전히 같다. 여기서는 **이 서비스가 무엇을 내보내는지**만 짚는다.

```
[하나의 트랜잭션]
    ├─ wishlist_items에 새 관심종목 저장(또는 마지막 관심 유저 삭제)
    └─ outbox_events에 "이 종목 구독 켜줘/꺼줘" 이벤트 기록
    → 둘 다 성공하거나 둘 다 실패

[KafkaOutboxPublisher — 5초 주기 폴링]
    ├─ 미발행 이벤트 조회
    ├─ Kafka(wishlist.symbol-subscription.v1)로 발행 (key = symbol)
    └─ 발행 완료 처리
```

이벤트가 딱 2종류뿐이다:

| 이벤트 | 발생 시점 |
|---|---|
| `WishlistSymbolActivated` | 어떤 종목의 활성 관심 유저가 0명 → 1명이 되는 순간 |
| `WishlistSymbolDeactivated` | 어떤 종목의 활성 관심 유저가 1명 → 0명이 되는 순간 |

**"매번" 발행하지 않는다**는 점이 핵심이다 — 이미 관심 유저가 3명인 종목에 4번째 사람이 추가돼도 이벤트는 안 나간다(market-service 입장에서는 "이미 구독 중"이라 알 필요가 없다). market-service는 이 이벤트를 log compaction(같은 key의 옛날 메시지는 자동 정리)으로 받아서, 재시작해도 "최신 상태"만 복원한다.

---

## 8. 데이터베이스 테이블 설명

이 서비스는 `wishlist` 스키마(DB는 `candle` — PostgreSQL 인스턴스를 스키마로만 분리해서 공유) 하나만 쓴다.

### `wishlist_items` — 관심종목

```sql
CREATE TABLE wishlist_items (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL,
  symbol        VARCHAR(20) NOT NULL,
  display_name  VARCHAR(100),
  market        VARCHAR(20),
  created_at    TIMESTAMPTZ NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL,
  deleted_at    TIMESTAMPTZ            -- soft delete. NULL이면 활성
);

-- 활성 항목 기준으로만 유저+종목 유일성 보장(soft delete라 일반 UNIQUE는 못 씀)
CREATE UNIQUE INDEX uk_wishlist_items_user_symbol_active
  ON wishlist_items(user_id, symbol) WHERE deleted_at IS NULL;

-- "이 종목 관심 유저가 몇 명이나 되나" 조회 가속
CREATE INDEX idx_wishlist_items_symbol_active
  ON wishlist_items(symbol) WHERE deleted_at IS NULL;
```

**갱신 사항 — 왜 진짜로 지우지 않는가(soft delete)**: 직접 삭제 대신 `deleted_at` 시각을 남긴다. `deleted_at IS NULL`이면 활성, 값이 있으면 삭제된 것으로 취급한다. 이렇게 하면 "언제 삭제했는지"도 같이 남길 수 있다.

### `wishlist_price_alerts` — 알림 트리거 이력

```sql
CREATE TABLE wishlist_price_alerts (
  id                     UUID PRIMARY KEY,
  user_id                UUID NOT NULL,
  symbol                 VARCHAR(20) NOT NULL,
  trading_date           DATE NOT NULL,
  direction              VARCHAR(10) NOT NULL,   -- RISE / FALL
  threshold_basis_points INT NOT NULL,
  open_price             BIGINT NOT NULL,
  trigger_price          BIGINT NOT NULL,
  change_basis_points    INT NOT NULL,
  notification_id        UUID,                   -- NULL이면 아직 발송 안 됨(재시도 대상)
  idempotency_key        VARCHAR(128) NOT NULL,
  triggered_at           TIMESTAMPTZ NOT NULL,
  created_at             TIMESTAMPTZ NOT NULL,
  updated_at             TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_wishlist_price_alerts_once
      UNIQUE(user_id, symbol, trading_date, direction, threshold_basis_points),
  CONSTRAINT uk_wishlist_price_alerts_idempotency UNIQUE(idempotency_key)
);

CREATE INDEX idx_wishlist_price_alerts_symbol_date ON wishlist_price_alerts(symbol, trading_date);
-- 재시도 스케줄러 전용 인덱스: "아직 안 보낸 것만" 빠르게 찾는다
CREATE INDEX idx_wishlist_price_alerts_retry ON wishlist_price_alerts(created_at) WHERE notification_id IS NULL;
```

유니크 제약이 **두 개**나 걸려있다 — 6절에서 설명한 "이중 안전장치"가 DB 스키마에도 그대로 드러난다.

### `market_open_snapshots` — 종목별 당일 시가/최근가 스냅샷

```sql
CREATE TABLE market_open_snapshots (
  id                        UUID PRIMARY KEY,
  symbol                    VARCHAR(20) NOT NULL,
  trading_date              DATE NOT NULL,
  open_price                BIGINT NOT NULL,       -- CHECK: > 0
  first_seen_at             TIMESTAMPTZ NOT NULL,
  last_price                BIGINT,                 -- CHECK: NULL 이거나 > 0
  last_change_basis_points  INTEGER,
  updated_at                TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_market_open_snapshots_symbol_date UNIQUE(symbol, trading_date)
);

CREATE INDEX idx_market_open_snapshots_date ON market_open_snapshots(trading_date);
```

이 테이블이 있는 이유: 등락률을 계산하려면 "오늘 시가가 얼마였는지"를 알아야 하는데, 매번 chart-service 같은 다른 서비스에 물어보면 느리다. 그래서 그날 처음 들어온 시세의 시가를 여기 적어두고 계속 재사용한다(일종의 캐시).

### `outbox_events` — 발행 대기 이벤트

```sql
CREATE TABLE outbox_events (
  id           UUID PRIMARY KEY,
  event_type   VARCHAR(120) NOT NULL,   -- "WishlistSymbolActivated" / "WishlistSymbolDeactivated"
  aggregate_id VARCHAR(120) NOT NULL,   -- symbol
  payload      TEXT NOT NULL,
  occurred_at  TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ              -- NULL이면 미발행
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at) WHERE published_at IS NULL;
```

`idempotency_records` 테이블은 이 서비스에 없다(6절에서 설명한 이유).

---

## 9. API 계약

### gRPC — `WishlistService`

| RPC | 요청 | 응답 | 비고 |
|---|---|---|---|
| `AddWishlistItem` | `user_id`, `symbol`, `display_name`, `market`, `command_metadata` | 등록된 관심종목 | 멱등성 키는 형식만 검증(6절) |
| `RemoveWishlistItem` | `user_id`, `symbol`, `command_metadata` | 빈 응답 | 〃 |
| `ListWishlistItems` | `user_id`, `page_request{page_size, page_token}` | 목록 + 다음 페이지 토큰 | 오프셋 기반 페이지네이션(기본 20건, 최대 100건) |

### 이 서비스가 호출하는 바깥 API

| 대상 | 방식 | 용도 |
|---|---|---|
| notification-service | gRPC `CreateNotification` | 급등락 알림 발송 |
| market-service | Redis Pub/Sub 구독 | 실시간 시세 수신 |
| market-service | Kafka 발행(`wishlist.symbol-subscription.v1`) | 실시간 시세 구독 켜줘/꺼줘 신호 |

---

## 10. 핵심 설계 결정 요약

| 결정 | 이유 |
|------|------|
| 관심종목 등록은 gRPC + Outbox | 즉시 응답이 필요하고, DB 변경과 이벤트 발행의 원자성이 중요하다 |
| 실시간 시세 수신은 Kafka 대신 Redis Pub/Sub(Chatting Service와 동일 패턴) | 시세는 초 단위로 쏟아지는 데이터라 가볍고 빠른 전달이 우선이고, 한두 건 유실돼도 다음 시세로 금방 갱신되니 치명적이지 않다 |
| 구독 수요 신호는 Redis 대신 Kafka로 발행 | 이건 "반드시 전달돼야 하는" 상태 변경 신호라, 유실 가능한 Pub/Sub 대신 outbox+재시도가 있는 Kafka를 쓴다 |
| 키 기반 멱등성 저장소(`idempotency_records`) 없음 | 관심종목 등록은 "자연 멱등"하게 설계할 수 있어서, 별도 저장소 없이 upsert + DB 유니크 인덱스로 충분하다고 판단 |
| 알림 중복 방지는 결정론적 키 + DB 유니크 제약 이중화 | 같은 조건의 알림이 여러 경로(재시도 등)로 두 번 나가는 걸 반드시 막아야 하는 부분이라 이중 안전장치를 걸었다 |
| 시세 평가(DB 저장)와 알림 발송(gRPC 호출)을 별도 트랜잭션으로 분리 | 외부 서비스 호출을 트랜잭션 안에 넣으면, 그 서비스가 느려질 때 DB 커넥션을 오래 붙잡게 된다 — 저장은 빠르게 끝내고 발송은 트랜잭션 밖에서 별도로 재시도 가능하게 설계 |
| soft delete(`deleted_at`) | 관심종목은 "취소"라는 상태 개념보다 "있다/없다"가 더 자연스러워서, 상태값 컬럼 대신 삭제 시각 컬럼을 썼다 |
| 관심 유저 0↔1 전이 시점에만 이벤트 발행 | 이미 구독 중인 종목에 유저가 추가/삭제될 때마다 이벤트를 보내면 market-service에 불필요한 신호가 쏟아진다 |

---

## 11. 테스트는 어떻게 검증하나

### 있는 테스트 — Fake를 직접 만들어 쓰는 방식

`DefaultWishlistServiceTest`는 Mockito Mock이 아니라 **직접 만든 가짜 구현체(Fake)**를 쓴다.

```java
// DefaultWishlistServiceTest.java (일부)
private static class FakeWishlistRepository implements WishlistItemReader, WishlistItemWriter {
    private final List<WishlistItem> items = new ArrayList<>();

    @Override
    public Optional<WishlistItem> findActive(UUID userId, String symbol) {
        return items.stream()
                .filter(WishlistItem::active)
                .filter(item -> item.getUserId().equals(userId))
                .filter(item -> item.getSymbol().equals(symbol))
                .findFirst();
    }
    // ... 실제 인메모리 리스트로 동작하는 진짜 구현체
}
```

**비유**: Mockito Mock이 "이렇게 물어보면 이렇게 답해"라고 미리 답을 정해두는 방식이라면, Fake는 진짜로 동작하는 미니어처 모형이다(장난감 자동차가 실제로 바퀴가 굴러가는 것처럼). 그래서 "0→1로 늘 때만 이벤트가 나가는지" 같은, 여러 단계를 거쳐야 확인되는 로직을 검증하기에 더 적합하다.

| 테스트 파일 | 검증 방식 |
|---|---|
| `DefaultWishlistServiceTest` | 손수 만든 Fake(`FakeWishlistRepository`, `RecordingOutboxWriter`)로 등록/삭제, 0→1/1→0 전이 조건 검증 |
| `PriceChangeCalculatorTest` | 순수 단위 테스트(Mock 없음), 등락률 계산 |
| `WishlistServiceApplicationTest` | 스모크 테스트(클래스 존재 확인만) |


### 로컬에서 테스트 실행하기

```bash
./gradlew :wishlist-service:test
```

---

*이 문서는 dev 브랜치의 실제 코드(WishlistGrpcService, DefaultWishlistService, PriceAlertService, PriceChangeCalculator, RedisMarketQuoteSubscriber, GrpcNotificationClient, KafkaOutboxPublisher, 각 엔티티), Flyway 마이그레이션, application.yml 및 3개 테스트 파일을 기준으로 작성했다.*
