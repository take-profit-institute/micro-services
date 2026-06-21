# Candle Batch

Spring Batch 기반의 독립 실행 모듈입니다. 외부 클라이언트 요청을 처리하는 gRPC 서비스가 아닙니다.

## 작업 후보

- 예약 주문 실행·만료 처리
- 미션·챌린지 마감 처리
- 만료된 멱등성 키 정리
- 미발행 outbox 재발행 및 실패 이벤트 재처리
- 조회 모델 재구축과 운영성 데이터 정리

각 Job은 `jobs/<도메인>/` 아래에 `JobConfig`, `Step`, `Reader`, `Processor`, `Writer`를 둡니다. Job은 서비스 DB를 직접 수정하지 않고, 소유 서비스의 명령 gRPC를 호출하거나 해당 서비스가 발행한 이벤트를 소비하는 방식을 기본으로 합니다. 공유 DB가 필요한 예외는 소유 서비스와 트랜잭션·락·재실행 규칙을 명시적으로 합의해야 합니다.

```bash
./gradlew :batch:bootRun
./gradlew :batch:test
```
