# Candle services

Microservice-only Gradle monorepo for Candle.

`common` is an internal library module, not a deployable microservice. It holds only cross-cutting contracts and utilities; service business entities, repositories, and database models remain inside their owning service.

## Services

| Module | Responsibility | Default port |
| --- | --- | --- |
| `auth-service` | OAuth login and JWT issuance | 8081 |
| `user-service` | User profile and preferences | 8082 |
| `market-service` | Quotes and instrument search | 8083 |
| `trading-service` | Orders, balances, holdings, and trade returns | 8084 |
| `portfolio-service` | Portfolio views and analysis | 8085 |
| `ranking-service` | Leaderboards | 8086 |
| `mission-service` | Challenges and rewards | 8087 |
| `learning-service` | Investment content and quizzes | 8088 |
| `notification-service` | Email and push delivery | 8089 |
| `bff-service` | Client-facing API composition | 8090 |

`trading-service` owns the former account and trading responsibilities. No service directly depends on another service's database or Java code.

## Run

```bash
./gradlew :services:trading-service:bootRun
./gradlew test
```
