# Auth Service 기능·연동·운영 가이드

## 1. 문서 목적과 책임

Auth Service는 Candle의 인증 진입점이다. OAuth 로그인으로 사용자를 식별하고,
access token과 refresh token을 발급·회전·폐기한다. 신규 사용자가 처음 로그인하면
`UserCreated` 이벤트를 Outbox에 기록하고 Kafka로 발행해 User Service가 프로필을 만들 수 있게 한다.

Auth가 소유하는 책임은 다음과 같다.

- OAuth provider 목록 제공과 OAuth authorization code 로그인
- JWT access token 발급
- refresh token 저장, 회전, 로그아웃 폐기
- 인증 사용자 정보 조회
- 관리자 로그인과 관리자 계정 부트스트랩
- 신규 사용자 생성 이벤트 Outbox 기록과 Kafka 발행

Auth가 소유하지 않는 책임은 다음과 같다.

- 사용자 프로필, 닉네임, 프로필 이미지 관리
- 주문, 포트폴리오, 랭킹 참가자 상태 관리
- 다른 서비스 DB 직접 수정
- 클라이언트 화면 라우팅

## 2. 전체 아키텍처와 데이터 흐름

![Auth Service 전체 아키텍처](assets/auth-architecture-overview.svg)

```text
Client/BFF
→ AuthController.login()
→ DefaultOAuthLoginService.login()
→ OAuthClientRegistry.resolve(provider).fetch()
→ OAuthAccountRepository.find/save()
→ DefaultAuthTokenService.issue()
→ OutboxWriter.recordUserCreated()
→ outbox_events 저장
→ KafkaOutboxPublisher.publishPendingEvents()
→ auth.user-created.v1 발행
```

## 3. 기능 진입점

### 3.1 HTTP

`auth-service`는 Gateway가 직접 라우팅하는 인증 서비스라 REST Controller를 가진다.

| HTTP API | 진입 메서드 | Service | 목적 |
| --- | --- | --- | --- |
| `GET /api/v1/auth/providers` | `AuthController.listProviders()` | `OAuthProvidersService.listProviders()` | 사용 가능한 OAuth provider와 authorization URL 조회 |
| `POST /api/v1/auth/oauth/{provider}` | `AuthController.login()` | `OAuthLoginService.login()` | OAuth code 로그인, 토큰 발급 |
| `POST /api/v1/auth/token/refresh` | `AuthController.refresh()` | `RefreshTokenService.rotate()` | refresh token 회전 |
| `POST /api/v1/auth/logout` | `AuthController.logout()` | `RefreshTokenService.revoke()` | refresh token 폐기, 쿠키 만료 |

### 3.2 gRPC

| RPC | 진입 메서드 | Service | 목적 |
| --- | --- | --- | --- |
| `ListProviders` | `AuthGrpcService.listProviders()` | `OAuthProvidersService.listProviders()` | 내부 서비스에서 provider 목록 조회 |
| `GetMe` | `AuthGrpcService.getMe()` | `AuthMeService.getMe()` | user_id 기준 인증 계정 조회 |
| `AdminLogin` | `AuthGrpcService.adminLogin()` | `AdminLoginService.login()` | 관리자 로그인 |

### 3.3 Scheduler

| 작업 | 주기 | 역할 |
| --- | --- | --- |
| `KafkaOutboxPublisher.publishPendingEvents()` | `${auth.outbox.publish-interval-ms:5000}` | 미발행 Auth Outbox 이벤트를 Kafka로 발행 |

## 4. 외부 서비스 계약

### 4.1 OAuth provider

| Provider | Client |
| --- | --- |
| Google | `GoogleOAuthClient` |
| Kakao | `KakaoOAuthClient` |
| Naver | `NaverOAuthClient` |

`DefaultOAuthLoginService.login()`은 provider client가 반환한 `OAuthProfile`의
`emailVerified`와 `subject`를 검증한다. 검증 실패 시 계정과 토큰을 만들지 않는다.

### 4.2 Kafka 이벤트

#### `UserCreated`

- event type: `UserCreated`
- topic: `auth.user-created.v1`
- publisher: `KafkaOutboxPublisher`
- partition key: `aggregate_id = userId`
- payload: `UserCreatedEvent`

| 필드 | 설명 |
| --- | --- |
| `eventId` | 이벤트 UUID |
| `eventType` | `UserCreated` |
| `eventVersion` | 현재 `1` |
| `userId` | Auth가 생성한 사용자 UUID |
| `email` | OAuth provider에서 받은 이메일 |
| `occurredAt` | 이벤트 생성 시각 |

User Service, Trading Service, Ranking Service는 이 이벤트를 구독해 각자 필요한 사용자 투영을 만든다.

## 5. 데이터 저장 결과

| 테이블 | 주요 컬럼 | 역할 |
| --- | --- | --- |
| `oauth_accounts` | `user_id`, `provider`, `provider_subject`, `email` | OAuth 계정 원본 |
| `refresh_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at` | refresh token 저장·회전·폐기 |
| `outbox_events` | `id`, `event_type`, `aggregate_id`, `payload`, `published_at` | Auth 이벤트 발행 대기열 |
| `admin_accounts` | migration `V20260705_003` | 관리자 계정 |

로그인에서 신규 사용자라면 `oauth_accounts` 저장과 `outbox_events` 기록은
`DefaultOAuthLoginService.login()`의 같은 트랜잭션 안에서 처리된다.

## 6. 핵심 정책

- OAuth 계정은 `(provider, provider_subject)`로 중복을 방지한다.
- access token은 RS256 JWT로 발급한다.
- refresh token 원문은 저장하지 않고 SHA-256 hash만 저장한다.
- refresh token 회전 시 기존 token은 `revoked_at`으로 폐기하고 새 token을 발급한다.
- 로그아웃은 refresh token이 있으면 폐기하고 access/refresh cookie를 만료시킨다.
- 신규 사용자 이벤트는 Outbox 패턴으로 발행한다.
- Kafka 발행은 at-least-once이며 소비자는 event ID로 중복을 방지해야 한다.

## 7. 서비스 영향도

![Auth Service 영향도](assets/auth-service-impact.svg)

| 상황 | 영향 |
| --- | --- |
| OAuth provider 장애 | 신규 로그인과 재로그인이 실패한다. 기존 access token이 유효한 사용자는 만료 전까지 계속 사용할 수 있다. |
| Auth DB 장애 | 로그인, refresh, logout, 인증 사용자 조회가 실패한다. |
| Auth Outbox 발행 지연 | 신규 사용자는 Auth에는 생성됐지만 User/Trading/Ranking 투영 생성이 늦어진다. Outbox 재시도 후 복구된다. |
| Kafka 장애 | Auth DB commit은 유지되고 `outbox_events.published_at`이 비어 있어 다음 publisher 실행에서 재시도한다. |
| JWT private key 미설정 | dev에서는 임시 키가 생성될 수 있으나 운영은 반드시 비밀값을 주입해야 한다. |

## 8. 테스트 방법

### 8.1 단위 테스트

```bash
./gradlew :services:auth-service:test
```

주요 검증 대상:

- `AuthControllerTest`: 로그인, refresh, logout HTTP 응답
- `AuthGrpcServiceTest`: provider 조회, getMe, adminLogin gRPC 응답과 오류 변환
- `DefaultOAuthLoginServiceTest`: 신규/기존 OAuth 로그인과 UserCreated Outbox 기록
- `DefaultAuthTokenServiceTest`: JWT 발급, refresh token 회전·폐기
- `OutboxWriterTest`: UserCreated payload와 Outbox 저장

### 8.2 로컬 DB 확인

```bash
docker compose exec postgres \
  psql -U candle -d candle -P pager=off -c "
SELECT user_id, provider, email
FROM auth.oauth_accounts
ORDER BY created_at DESC
LIMIT 5;

SELECT event_type, aggregate_id, published_at
FROM auth.outbox_events
ORDER BY occurred_at DESC
LIMIT 5;
"
```

정상 결과:

- 신규 로그인 후 `oauth_accounts`에 사용자 계정이 생긴다.
- 신규 사용자라면 `outbox_events`에 `UserCreated`가 남고, 발행 완료 후 `published_at`이 채워진다.

## 9. 운영 설정과 향후 변경 지점

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `AUTH_SERVER_PORT` | `8081` | HTTP port |
| `AUTH_GRPC_PORT` | `50051` | gRPC port |
| `AUTH_DB_URL` | `jdbc:postgresql://localhost:5432/candle?currentSchema=auth,public` | Auth DB schema |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `AUTH_OUTBOX_PUBLISH_INTERVAL_MS` | `5000` | Outbox 발행 주기 |
| `AUTH_JWT_PRIVATE_KEY` | 빈 값 | 운영에서는 반드시 주입 |

향후 변경 지점:

- 회원 탈퇴·정지 정책이 확정되면 Auth 또는 User 소유 이벤트 계약을 별도 문서에 추가한다.
- OAuth provider별 scope, redirect URI 정책이 바뀌면 `AuthProperties`와 provider client 문서를 갱신한다.
- JWT claim이 추가되면 Gateway/BFF/서비스 인증 interceptor 영향도를 함께 확인한다.
