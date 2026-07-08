# User Service 기능·연동·운영 가이드

![User architecture overview](./assets/user-architecture-overview.svg)

## 1. 문서 목적과 책임

User Service는 Candle 사용자의 프로필 Read/Write 모델을 소유한다. Auth가 발행한
`UserCreated` 이벤트를 소비해 기본 프로필을 만들고, 사용자의 프로필 수정 요청을
멱등하게 처리한 뒤 `UserProfileUpdated` 이벤트를 Outbox로 발행한다.

User가 소유하는 책임은 다음과 같다.

- 사용자 프로필 조회
- 닉네임과 프로필 이미지 수정
- Auth `UserCreated` 이벤트 기반 기본 프로필 생성
- 프로필 수정 상태 변경 RPC의 멱등성 처리
- 프로필 변경 이벤트 Outbox 기록과 Kafka 발행

User가 소유하지 않는 책임은 다음과 같다.

- OAuth 로그인, token 발급, refresh token 관리
- 주문·계좌·포트폴리오·랭킹 원본 상태 관리
- 다른 서비스 DB 직접 수정
- 사용자 상태 변경·탈퇴 이벤트 발행 정책 확정

## 2. 전체 아키텍처와 데이터 흐름

![User Service 전체 아키텍처](assets/user-architecture-overview.svg)

### 2.1 신규 사용자 프로필 생성

```text
Auth Service
→ auth.user-created.v1
→ UserCreatedEventConsumer.onUserCreated()
→ consumed_events 중복 확인
→ DefaultProfileGenerator.generate()
→ user_profiles 저장
→ consumed_events 저장
```

### 2.2 프로필 조회·수정

```text
BFF / internal client
→ UserGrpcService
→ GetMe: UserProfileService.getProfile()
→ UpdateProfile: IdempotencyExecutor.execute()
→ DefaultUserProfileService.updateProfile()
→ UserProfileEntity.updateProfile()
→ user_profiles 저장
→ user_outbox_events 저장
→ OutboxEventBatchPublisher.publish()
→ user.profile-updated.v1 발행
```

## 3. 기능 진입점

### 3.1 gRPC

| RPC | 진입 메서드 | Service | 목적 |
| --- | --- | --- | --- |
| `GetMe` | `UserGrpcService.getMe()` | `UserProfileService.getProfile()` | 인증 사용자 프로필 조회 |
| `UpdateProfile` | `UserGrpcService.updateProfile()` | `UserProfileService.updateProfile()` | 닉네임·프로필 이미지 수정 |

`UserGrpcService.requireActor()`는 metadata에서 인증 actor를 읽고, request의 `user_id`가 있으면
actor와 같은지 확인한다. 다른 사용자의 프로필을 요청하면 `PERMISSION_DENIED`로 실패한다.

### 3.2 Kafka 소비

| Topic | Consumer | 결과 |
| --- | --- | --- |
| `auth.user-created.v1` | `UserCreatedEventConsumer.onUserCreated()` | 기본 프로필 생성, `consumed_events` 기록 |

### 3.3 Scheduler

| 작업 | 주기 | 역할 |
| --- | --- | --- |
| `OutboxEventBatchPublisher.publish()` | 1초 고정 delay | 미발행 `user_outbox_events` 최대 100건 Kafka 발행 |

## 4. 외부 서비스 계약

### 4.1 Auth 이벤트 입력

#### `UserCreated`

- topic: `auth.user-created.v1`
- payload DTO: `UserCreatedPayload`
- consumer group: `user-service`
- 중복 기준: `consumed_events.event_id`

| 필드 | 사용처 |
| --- | --- |
| `eventId` | 중복 소비 방지 |
| `eventType` | 소비 이력 저장 |
| `eventVersion` | 현재 payload version |
| `userId` | `user_profiles.user_id` |
| `email` | `user_profiles.email` |
| `occurredAt` | 현재 코드에서는 저장하지 않음 |

역직렬화 실패 이벤트는 로그를 남기고 처리하지 않는다. 같은 이벤트가 재전달되면
`consumed_events`로 skip한다.

### 4.2 User 이벤트 출력

#### `UserProfileUpdated`

- topic: `user.profile-updated.v1`
- publisher: `OutboxEventBatchPublisher`
- partition key: `userId`
- event type: `UserProfileUpdated`

| 필드 | 설명 |
| --- | --- |
| `eventId` | 이벤트 UUID |
| `eventType` | `UserProfileUpdated` |
| `eventVersion` | 현재 `1` |
| `userId` | 프로필 사용자 |
| `nickname` | 최신 닉네임 |
| `profileImageUrl` | 최신 프로필 이미지 URL |
| `occurredAt` | 이벤트 생성 시각 |

Ranking Service는 이 이벤트를 소비해 랭킹 화면의 닉네임을 최신화한다.

## 5. 데이터 저장 결과

| 테이블 | 주요 컬럼 | 역할 |
| --- | --- | --- |
| `user_profiles` | `user_id`, `email`, `nickname`, `profile_image_url`, `deleted`, `version` | 프로필 원본 |
| `consumed_events` | `event_id`, `event_type`, `consumed_at` | Auth 이벤트 중복 소비 방지 |
| `user_idempotency_records` | `actor_id`, `operation`, `idempotency_key`, `request_hash`, `response_payload` | `UpdateProfile` 응답 재생 |
| `user_outbox_events` | `id`, `topic`, `partition_key`, `payload`, `published` | User 이벤트 발행 대기열 |

프로필 수정 시 `user_profiles` 저장과 `user_outbox_events` 기록은 같은 service 트랜잭션에서 처리된다.
멱등성 저장은 `IdempotencyExecutor`가 request hash와 성공 응답 bytes를 함께 관리한다.

## 6. 핵심 정책

- `GetMe`는 인증 actor와 request `user_id`가 일치해야 한다.
- `UpdateProfile`은 `command_metadata.idempotency_key`와 metadata key를 기반으로 멱등하게 처리한다.
- 같은 key와 같은 payload는 저장된 응답을 재생한다.
- 같은 key와 다른 payload는 멱등성 충돌로 실패한다.
- 사용자 프로필 Entity는 public setter 없이 `updateProfile()`로만 변경한다.
- Auth 이벤트는 at-least-once로 들어올 수 있으므로 `consumed_events`로 중복을 막는다.
- User 이벤트는 Outbox로 발행하며 Kafka 발행 실패 시 DB commit은 유지된다.

## 7. 서비스 영향도

![User Service 영향도](assets/user-service-impact.svg)

| 상황 | 영향 |
| --- | --- |
| Auth `UserCreated` 미수신 | 사용자가 Auth에는 있어도 User 프로필 조회가 `USER_NOT_FOUND`로 실패할 수 있다. |
| User DB 장애 | 프로필 조회·수정과 이벤트 소비가 실패한다. |
| User Outbox 발행 지연 | 프로필 수정은 저장되지만 Ranking 등 downstream 닉네임 반영이 늦어진다. |
| Kafka 장애 | `user_outbox_events.published=false` 상태로 남고 다음 publisher 실행에서 재시도한다. |
| Redis 영향 | User Service는 Redis를 사용하지 않는다. |

## 8. 테스트 방법

### 8.1 단위 테스트

```bash
./gradlew :services:user-service:test
```

주요 검증 대상:

- `UserGrpcServiceTest`: actor 검증, GetMe/UpdateProfile 응답과 오류 변환
- `DefaultUserProfileServiceTest`: 조회·수정·Outbox 기록
- `UserCreatedEventConsumerTest`: Auth 이벤트 소비, 중복 skip, 이미 존재하는 프로필 처리
- `IdempotencyExecutorTest`: 응답 재생, 다른 payload 충돌
- `RequestHasherTest`: deterministic hash
- `OutboxWriterTest`: `UserProfileUpdated` payload와 Outbox 저장

### 8.2 로컬 DB 확인

```bash
docker compose exec postgres \
  psql -U candle -d candle -P pager=off -c "
SELECT user_id, email, nickname, deleted, version
FROM users.user_profiles
ORDER BY created_at DESC
LIMIT 5;

SELECT event_id, event_type, consumed_at
FROM users.consumed_events
ORDER BY consumed_at DESC
LIMIT 5;

SELECT topic, partition_key, published
FROM users.user_outbox_events
ORDER BY created_at DESC
LIMIT 5;
"
```

정상 결과:

- Auth `UserCreated`를 소비하면 `user_profiles`에 기본 프로필이 생긴다.
- 같은 `event_id`는 `consumed_events` 때문에 한 번만 반영된다.
- 프로필 수정 후 `user_outbox_events`에 `user.profile-updated.v1` 이벤트가 생기고 발행 후 `published=true`가 된다.

## 9. 운영 설정과 향후 변경 지점

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `USER_SERVER_PORT` | `8082` | HTTP/actuator port |
| `USER_GRPC_PORT` | `50052` | gRPC port |
| `USER_DB_URL` | `jdbc:postgresql://localhost:5432/candle?currentSchema=users,public` | User DB schema |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `USER_IDEMPOTENCY_TTL` | `P1D` | 멱등성 응답 보관 기간 |

향후 변경 지점:

- 탈퇴, 정지, 비활성 사용자 정책이 확정되면 `UserStatusChanged` 이벤트 계약과 소비자 영향도를 추가한다.
- 프로필 이미지 저장소가 확정되면 `profile_image_url` 검증 정책을 갱신한다.
- 닉네임 중복 허용 여부가 바뀌면 DB 제약과 서비스 검증 정책을 함께 갱신한다.
