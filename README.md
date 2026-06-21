# Candle 서비스

Candle 마이크로서비스 전용 Gradle 모노레포입니다.

`common`은 배포하지 않는 내부 라이브러리 모듈입니다. 서비스 간 공통 계약과 유틸리티만 두며, 비즈니스 Entity·Repository·DB 모델은 각 소유 서비스 내부에 둡니다.

- 코드·아키텍처 컨벤션: [docs/CONVENTIONS.md](docs/CONVENTIONS.md)
- 쓰기 명령 멱등성: [docs/IDEMPOTENCY.md](docs/IDEMPOTENCY.md)
- Spring Batch 운영 규칙: [docs/BATCH.md](docs/BATCH.md)
- 서비스별 환경 변수 관리: [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md)
- 내부 gRPC 계약: [proto/README.md](proto/README.md)

## 서비스

| 모듈 | 책임 | 기본 포트 |
| --- | --- | --- |
| `auth-service` | OAuth 로그인과 JWT 발급 | 8081 |
| `user-service` | 사용자 프로필과 환경 설정 | 8082 |
| `market-service` | 실시간 시세와 종목 검색 | 8083 |
| `trading-service` | 주문, 잔고, 보유종목, 거래 수익률 | 8084 |
| `portfolio-service` | 포트폴리오 조회와 분석 | 8085 |
| `ranking-service` | 리더보드 집계와 조회 | 8086 |
| `mission-service` | 미션, 챌린지, 보상 | 8087 |
| `learning-service` | 투자 콘텐츠와 퀴즈 | 8088 |
| `notification-service` | 이메일과 푸시 알림 발송 | 8089 |
| `batch` | 예약·마감·정리 등 스케줄 작업 실행 | 없음 |

`auth-service`는 Gateway가 직접 라우팅하는 OAuth/JWT HTTP 서비스입니다. 나머지 도메인 서비스 간 동기 통신은 gRPC를 사용합니다. `trading-service`는 기존 account와 trading 책임을 함께 소유합니다. 어떤 서비스도 다른 서비스의 DB나 Java 코드에 직접 의존하지 않습니다.

## 실행

실행 전 해당 서비스의 개발 환경 예시를 복사해 필요한 값을 채웁니다.

```bash
cp services/auth-service/.env.development.example services/auth-service/.env
```

```bash
./gradlew :services:trading-service:bootRun
./gradlew test
```
