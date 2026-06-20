# Candle 서비스

Candle 마이크로서비스 전용 Gradle 모노레포입니다.

`common`은 배포하지 않는 내부 라이브러리 모듈입니다. 서비스 간 공통 계약과 유틸리티만 두며, 비즈니스 Entity·Repository·DB 모델은 각 소유 서비스 내부에 둡니다.

- 코드·아키텍처 컨벤션: [docs/CONVENTIONS.md](docs/CONVENTIONS.md)
- BFF와 서비스 간 책임: [docs/BFF_GRPC_CONTRACT.md](docs/BFF_GRPC_CONTRACT.md)
- 쓰기 명령 멱등성: [docs/IDEMPOTENCY.md](docs/IDEMPOTENCY.md)
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
| `bff-service` | 클라이언트 API 데이터 조합 | 8090 |

`trading-service`는 기존 account와 trading 책임을 함께 소유합니다. 어떤 서비스도 다른 서비스의 DB나 Java 코드에 직접 의존하지 않습니다.

## 실행

```bash
./gradlew :services:trading-service:bootRun
./gradlew test
```
